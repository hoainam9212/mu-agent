from types import SimpleNamespace

import pytest

from vaohang.models import Fixture, make_sessionmaker
from vaohang.pats import LINES_PER_FIXTURE, PatContext, choices_for, ensure_lines, filter_lines
from vaohang.pats.bank import BANK, VERIFIED_FACTS, scenario_tags
from vaohang.pats.claude import ClaudeLineGenerator, _Judgement, _Lines, _Verdict


def ctx(result="L", margin=2, streak=1, days=0):
    return PatContext(opponent="Atlético Madrid", competition="UEFA Champions League",
                      score="Atlético Madrid 2-0 MU", result=result, margin=margin, non_win_streak=streak,
                      cave_days=days, facts=list(VERIFIED_FACTS))


@pytest.mark.parametrize("line", [b.text for b in BANK])
def test_every_bank_line_passes_filters(line):
    c = ctx()
    text = line.format(opponent=c.opponent, score=c.score)
    assert filter_lines([text], c) == [text]


@pytest.mark.parametrize(
    "line",
    [
        "Tại thằng thủ môn hôm nay thôi.",
        "Trọng tài ăn tiền rõ ràng.",
        "Anh em ơi, Bruno hôm nay đá như đi dạo.",
        "MU thua 5 trận liền rồi, buồn ghê.",  # số 5 không có trong dữ kiện
        "Fan bên kia giờ chắc vui lắm.",
        "x" * 130,
    ],
)
def test_filters_reject_blame_names_and_made_up_numbers(line):
    assert filter_lines([line], ctx()) == []


def test_scenario_tags():
    assert scenario_tags("L", 1, 1, 0) == {"any", "loss", "narrow"}
    assert scenario_tags("D", 0, 3, 8) == {"any", "draw", "streak", "long"}


class Boom:
    def generate(self, ctx):
        raise RuntimeError("network down")


class Fixed:
    def __init__(self, lines):
        self.lines = lines

    def generate(self, ctx):
        return self.lines


@pytest.fixture
def db():
    with make_sessionmaker("sqlite://")() as session:
        yield session


def _fixture(db):
    from datetime import datetime

    f = Fixture(provider="manual", provider_id="x", kickoff=datetime(2026, 10, 13, 19), opponent="Atlético Madrid",
                competition="UEFA Champions League", home=False, status="FINISHED", mu_goals=0, opp_goals=2,
                result="L")
    db.add(f)
    db.flush()
    return f


def test_generator_failure_falls_back_to_bank(db):
    f = _fixture(db)
    lines = ensure_lines(db, f, ctx(), Boom())
    assert len(lines) == LINES_PER_FIXTURE
    assert {l.source for l in lines} == {"bank"}


def test_ai_lines_first_then_bank_fill(db):
    f = _fixture(db)
    ai = ["Ngủ thôi anh em, mai còn đi làm.", "Trọng tài hôm nay tệ hại."]  # câu 2 bị lọc
    lines = ensure_lines(db, f, ctx(), Fixed(ai))
    assert lines[0].text == ai[0] and lines[0].source == "ai"
    assert all(l.text != ai[1] for l in lines)
    assert len(lines) == LINES_PER_FIXTURE
    # Gọi lại không sinh thêm
    assert len(ensure_lines(db, f, ctx(), Fixed(["khác"]))) == LINES_PER_FIXTURE


def test_choices_are_stable_per_user(db):
    f = _fixture(db)
    lines = ensure_lines(db, f, ctx(), None)
    a = [l.id for l in choices_for(lines, 1, f.id)]
    assert a == [l.id for l in choices_for(lines, 1, f.id)]
    assert len(a) == 4


class FakeMessages:
    def __init__(self, responses):
        self.responses = list(responses)
        self.calls = []

    def parse(self, **kwargs):
        self.calls.append(kwargs)
        return self.responses.pop(0)


def test_claude_generator_drops_lines_the_judge_flags():
    drafted = _Lines(lines=["Câu một ổn.", "Câu hai chê người.", "Câu ba ổn."])
    judged = _Judgement(verdicts=[
        _Verdict(index=0, blames_or_names_someone=False, uses_fact_not_given=False),
        _Verdict(index=1, blames_or_names_someone=True, uses_fact_not_given=False),
        _Verdict(index=2, blames_or_names_someone=False, uses_fact_not_given=False),
    ])
    messages = FakeMessages([
        SimpleNamespace(stop_reason="end_turn", parsed_output=drafted),
        SimpleNamespace(stop_reason="end_turn", parsed_output=judged),
    ])
    client = SimpleNamespace(beta=SimpleNamespace(messages=messages))
    gen = ClaudeLineGenerator("claude-opus-5-5", client=client)
    assert gen.generate(ctx()) == ["Câu một ổn.", "Câu ba ổn."]
    first = messages.calls[0]
    assert first["model"] == "claude-opus-5-5"
    assert first["fallbacks"] == "default"
    assert "Atlético Madrid 2-0 MU" in first["messages"][0]["content"]


def test_claude_generator_refusal_returns_nothing():
    messages = FakeMessages([SimpleNamespace(stop_reason="refusal", stop_details=None, parsed_output=None)])
    gen = ClaudeLineGenerator("claude-opus-5-5", client=SimpleNamespace(beta=SimpleNamespace(messages=messages)))
    assert gen.generate(ctx()) == []
