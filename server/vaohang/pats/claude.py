"""Sinh câu vỗ vai bằng Claude: 1 lượt soạn + 1 lượt chấm, mỗi trận một lần (không theo từng người)."""

from __future__ import annotations

import logging

import anthropic
from pydantic import BaseModel

from . import PatContext

log = logging.getLogger(__name__)

SYSTEM = """Bạn soạn câu "vỗ vai" cho một app của fan Manchester United người Việt. Sau trận MU không thắng,
anh em trong hội bấm một chạm để gửi câu này cho nhau. Không có ô chat, nên câu phải tự đứng được.

Luật bắt buộc:
- Tự trào về MU và về việc làm fan MU. Giọng anh em, thân mật, tiếng Việt tự nhiên, không teencode tục.
- Mỗi câu có cả đồng cảm và một góc nhìn mới giúp nguôi (chuyện dài hạn, giấc ngủ, tình anh em, lịch sử CLB…),
  không than thở dồn thêm.
- Không chê, không đổ lỗi, không nhắc tên bất kỳ cầu thủ, HLV, trọng tài nào; không nói về fan đội khác.
  Được khen đối thủ chơi hay.
- Chỉ dùng con số và dữ kiện có trong phần DỮ KIỆN. Không tự thêm số liệu.
- Mỗi câu tối đa 110 ký tự."""


class _Lines(BaseModel):
    lines: list[str]


class _Verdict(BaseModel):
    index: int
    blames_or_names_someone: bool
    uses_fact_not_given: bool


class _Judgement(BaseModel):
    verdicts: list[_Verdict]


def _facts_block(ctx: PatContext) -> str:
    result = "MU thua" if ctx.result == "L" else "MU hoà"
    facts = "\n".join(f"- {f}" for f in ctx.facts)
    return (
        f"DỮ KIỆN\n- Trận vừa xong: {ctx.score} ({ctx.competition}). {result}.\n"
        f"- Số trận liền MU không thắng: {ctx.non_win_streak}.\n"
        f"- Hội anh em đã ở trong hang {ctx.cave_days} ngày.\n{facts}"
    )


class ClaudeLineGenerator:
    def __init__(self, model: str, client: anthropic.Anthropic | None = None, count: int = 12):
        self.model = model
        # Đêm trận không đợi lâu được: hỏng thì quay về kho dự phòng.
        self.client = client or anthropic.Anthropic(timeout=60, max_retries=1)
        self.count = count

    def _parse(self, output_format, system: str, prompt: str, effort: str):
        response = self.client.beta.messages.parse(
            model=self.model,
            max_tokens=16000,
            system=system,
            messages=[{"role": "user", "content": prompt}],
            output_format=output_format,
            output_config={"effort": effort},
            betas=["server-side-fallback-2026-07-01"],
            fallbacks="default",
        )
        if response.stop_reason == "refusal":
            log.warning("pat generation refused: %s", response.stop_details)
            return None
        return response.parsed_output

    def generate(self, ctx: PatContext) -> list[str]:
        facts = _facts_block(ctx)
        try:
            drafted = self._parse(
                _Lines, SYSTEM, f"{facts}\n\nSoạn {self.count} câu vỗ vai khác nhau cho đêm nay.", "medium"
            )
            if not drafted or not drafted.lines:
                return []
            numbered = "\n".join(f"{i}. {line}" for i, line in enumerate(drafted.lines))
            judged = self._parse(
                _Judgement,
                "Bạn kiểm duyệt câu vỗ vai cho fan MU. Đánh giá nghiêm, nghi ngờ thì đánh dấu.",
                f"{facts}\n\nCÂU CẦN CHẤM\n{numbered}\n\nVới mỗi câu, cho biết: câu có chê/đổ lỗi hoặc nhắc tên "
                "cầu thủ, HLV, trọng tài, fan đội khác không; câu có dùng con số/dữ kiện không có trong DỮ KIỆN không.",
                "low",
            )
        except anthropic.RateLimitError:
            log.warning("pat generation rate limited; using bank")
            return []
        except anthropic.APIStatusError as e:
            log.warning("pat generation API error %s: %s", e.status_code, e.message)
            return []
        except anthropic.APIConnectionError:
            log.warning("pat generation network error; using bank")
            return []
        if not judged:
            return []
        bad = {v.index for v in judged.verdicts if v.blames_or_names_someone or v.uses_fact_not_given}
        judged_ok = {v.index for v in judged.verdicts} - bad
        return [line for i, line in enumerate(drafted.lines) if i in judged_ok]
