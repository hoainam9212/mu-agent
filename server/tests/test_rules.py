import pytest

from vaohang.rules import DRAW, LOSS, WIN, lights_on, mu_result, next_cave_open


@pytest.mark.parametrize(
    "is_open,result,expected",
    [
        (False, LOSS, True),
        (True, LOSS, True),
        (False, DRAW, False),
        (True, DRAW, True),
        (False, WIN, False),
        (True, WIN, False),
    ],
)
def test_next_cave_open(is_open, result, expected):
    assert next_cave_open(is_open, result) is expected


def test_loss_draw_draw_keeps_cave_open():
    is_open = False
    for result in (LOSS, DRAW, DRAW):
        is_open = next_cave_open(is_open, result)
        assert is_open
        assert lights_on(is_open, result)


def test_win_turns_lights_off():
    assert not lights_on(next_cave_open(True, WIN), WIN)


def test_mu_result_uses_shootout_winner():
    assert mu_result(1, 1) == DRAW
    assert mu_result(1, 1, mu_won_shootout=True) == WIN
    assert mu_result(1, 1, mu_won_shootout=False) == LOSS
    assert mu_result(0, 2) == LOSS
