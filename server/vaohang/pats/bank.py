"""Kho câu vỗ vai dự phòng và dữ kiện đã kiểm chứng.

CONTENT sở hữu file này. Mọi câu phải: tự trào về MU, có đồng cảm + một góc nhìn mới,
không chê/đổ lỗi cho cầu thủ, HLV, trọng tài hay fan đội khác, không bịa số liệu.
Thẻ: any (dùng được mọi lúc), loss, narrow (thua 1 bàn), heavy (thua từ 2 bàn), draw,
streak (≥ 2 trận liền không thắng), long (hang mở ≥ 7 ngày).
"""

from __future__ import annotations

from dataclasses import dataclass

# Dữ kiện đưa cho AI. Chỉ thêm điều đã kiểm chứng, kèm nguồn trong PR.
VERIFIED_FACTS: list[str] = [
    "MU đứng thứ 15 Ngoại hạng Anh mùa 2024-25.",
    "MU đã 20 lần vô địch giải vô địch quốc gia Anh.",
    "Mùa 1998-99 MU giành cú ăn ba.",
    "MU vô địch Europa League mùa 2016-17.",
]


@dataclass(frozen=True)
class BankLine:
    text: str
    tags: frozenset[str]


def _l(text: str, *tags: str) -> BankLine:
    return BankLine(text, frozenset(tags or ("any",)))


BANK: list[BankLine] = [
    _l("Làm fan MU còn chịu được cơ mà, thêm một đêm nữa có sá gì."),
    _l("Mùa 24-25 còn đứng thứ 15, đêm nay chưa là gì đâu.", "loss", "streak"),
    _l("Thua {score} thôi mà, hang mình còn rộng, cứ vào ngồi đã.", "loss"),
    _l("Cay thì cay, nhưng mai vẫn phải đi làm. Ngủ sớm cho đỡ cay."),
    _l("Fan MU mình lì đòn nhất hệ mặt trời rồi, đêm nay chỉ là tập thêm."),
    _l("Thua trận chứ không thua tình anh em. Vỗ vai cái rồi đi ngủ.", "loss"),
    _l("Ít ra đêm nay mình không phải thức một mình."),
    _l("Hôm nay {opponent} chơi hay hơn, công nhận. Trận sau mình tính tiếp.", "loss"),
    _l("Xem MU bao năm, mình kiên nhẫn hơn cả người đi câu cá."),
    _l("Còn cả mùa giải phía trước, đâu phải chung kết đâu."),
    _l("Thua {score} thì đau thật, nhưng ngủ một giấc là bớt nửa.", "heavy"),
    _l("Tỷ số {score} xem như học phí. Học phí làm fan MU năm nay hơi đắt.", "heavy"),
    _l("Đêm nay đặt điện thoại xuống sớm là thắng được một trận rồi.", "heavy", "loss"),
    _l("Thua sát nút thôi, một bàn thì trận sau gỡ lại được.", "narrow"),
    _l("Chỉ thua một bàn, nghĩa là mình chỉ thiếu chút may thôi.", "narrow"),
    _l("Hoà rồi, chưa ra được hang nhưng ít ra không phải đào sâu thêm.", "draw"),
    _l("Một điểm vẫn hơn không điểm. Hang vẫn ấm, anh em cứ ngồi.", "draw"),
    _l("Chưa thắng thì chưa ra, nhưng đã thấy le lói ánh sáng cuối hang.", "draw"),
    _l("Ở trong hang lâu quá, anh em sắp thành người tiền sử rồi.", "long", "streak"),
    _l("Chuỗi trận này rồi cũng hết, chứ làm fan MU thì không hết được.", "streak"),
    _l("Hang này quen quá rồi, lần sau ra nhớ mang theo đèn pin.", "long"),
    _l("Không sao, mai mặt trời vẫn mọc, MU vẫn đá tiếp."),
    _l("Chọn MU lúc MU hay thì dễ, ở lại lúc MU khó mới là fan."),
    _l("Đêm nay buồn, nhưng mai kể lại vẫn thành chuyện vui."),
    _l("Đi ngủ thôi anh em, giấc ngủ là bản hợp đồng tốt nhất đêm nay."),
    _l("Cùng thua, cùng vào hang, rồi cùng ra. Thế mới là anh em."),
    _l("Tắt máy đi, đừng đọc bình luận, đọc chỉ thêm cay."),
    _l("20 lần vô địch Anh rồi, đợi thêm chút nữa cũng được.", "streak", "long"),
]


def scenario_tags(result: str, margin: int, non_win_streak: int, cave_days: int) -> set[str]:
    tags = {"any"}
    if result == "L":
        tags |= {"loss", "heavy" if margin >= 2 else "narrow"}
    elif result == "D":
        tags.add("draw")
    if non_win_streak >= 2:
        tags.add("streak")
    if cave_days >= 7:
        tags.add("long")
    return tags


def pick_bank_lines(tags: set[str], opponent: str, score: str, limit: int, seed: int = 0) -> list[str]:
    import random

    specific = [b for b in BANK if b.tags & (tags - {"any"})]
    generic = [b for b in BANK if "any" in b.tags]
    rng = random.Random(seed)
    rng.shuffle(specific)
    rng.shuffle(generic)
    chosen = (specific + generic)[:limit]
    return [b.text.format(opponent=opponent, score=score) for b in chosen]
