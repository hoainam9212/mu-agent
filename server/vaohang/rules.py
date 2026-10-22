"""Luật hang (đã chốt 22/10/2026).

- Thua → hang mở (đang mở thì vẫn mở) và sáng đèn.
- Hoà → hang giữ nguyên; đang mở thì sáng đèn lại.
- Thắng → hang đóng, ai còn trong hang được đưa ra.
- Vào/ra hang là quyết định của người dùng (xem services/cave.py).
"""

from __future__ import annotations

WIN, DRAW, LOSS = "W", "D", "L"


def mu_result(mu_goals: int, opp_goals: int, mu_won_shootout: bool | None = None) -> str:
    """Kết quả của MU. Trận phân định bằng luân lưu tính theo kết quả luân lưu."""
    if mu_won_shootout is not None:
        return WIN if mu_won_shootout else LOSS
    if mu_goals > opp_goals:
        return WIN
    if mu_goals < opp_goals:
        return LOSS
    return DRAW


def next_cave_open(is_open: bool, result: str) -> bool:
    if result == WIN:
        return False
    if result == LOSS:
        return True
    return is_open


def lights_on(is_open_after: bool, result: str) -> bool:
    return is_open_after and result != WIN
