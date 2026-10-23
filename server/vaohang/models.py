"""Bảng dữ liệu. Thời điểm lưu dạng UTC không kèm tzinfo."""

from __future__ import annotations

from datetime import datetime

from sqlalchemy import (
    Boolean,
    DateTime,
    ForeignKey,
    Integer,
    String,
    Text,
    UniqueConstraint,
    create_engine,
)
from sqlalchemy.orm import DeclarativeBase, Mapped, mapped_column, sessionmaker


class Base(DeclarativeBase):
    pass


class User(Base):
    __tablename__ = "users"

    id: Mapped[int] = mapped_column(primary_key=True)
    nickname: Mapped[str] = mapped_column(String(40))
    token_hash: Mapped[str] = mapped_column(String(64), unique=True, index=True)
    fcm_token: Mapped[str | None] = mapped_column(Text)
    created_at: Mapped[datetime] = mapped_column(DateTime)


class Group(Base):
    __tablename__ = "groups"

    id: Mapped[int] = mapped_column(primary_key=True)
    name: Mapped[str] = mapped_column(String(60))
    invite_code: Mapped[str] = mapped_column(String(6), unique=True, index=True)
    owner_id: Mapped[int] = mapped_column(ForeignKey("users.id"))
    # Hội demo chỉ nhận trận replay, hội thật chỉ nhận trận thật.
    is_demo: Mapped[bool] = mapped_column(Boolean, default=False)
    created_at: Mapped[datetime] = mapped_column(DateTime)


class GroupMember(Base):
    __tablename__ = "group_members"
    __table_args__ = (UniqueConstraint("group_id", "user_id"),)

    id: Mapped[int] = mapped_column(primary_key=True)
    group_id: Mapped[int] = mapped_column(ForeignKey("groups.id"), index=True)
    user_id: Mapped[int] = mapped_column(ForeignKey("users.id"), index=True)
    # Hai bên cùng đồng ý: người xin vào (pending) + chủ hội duyệt (approved).
    status: Mapped[str] = mapped_column(String(10))  # pending | approved
    joined_at: Mapped[datetime] = mapped_column(DateTime)


class Fixture(Base):
    __tablename__ = "fixtures"
    __table_args__ = (UniqueConstraint("provider", "provider_id"),)

    id: Mapped[int] = mapped_column(primary_key=True)
    provider: Mapped[str] = mapped_column(String(20))  # football_data | api_football | manual | replay
    provider_id: Mapped[str] = mapped_column(String(64))
    kickoff: Mapped[datetime] = mapped_column(DateTime, index=True)
    opponent: Mapped[str] = mapped_column(String(80))
    competition: Mapped[str] = mapped_column(String(80), default="")
    home: Mapped[bool] = mapped_column(Boolean, default=True)
    status: Mapped[str] = mapped_column(String(12), default="SCHEDULED")
    mu_goals: Mapped[int | None] = mapped_column(Integer)
    opp_goals: Mapped[int | None] = mapped_column(Integer)
    result: Mapped[str | None] = mapped_column(String(1))  # W | D | L
    # Trận replay: thời điểm tự kết thúc (giờ thật, đã tua nhanh).
    replay_ends_at: Mapped[datetime | None] = mapped_column(DateTime)
    last_polled_at: Mapped[datetime | None] = mapped_column(DateTime)
    reminded_at: Mapped[datetime | None] = mapped_column(DateTime)
    processed_at: Mapped[datetime | None] = mapped_column(DateTime)

    @property
    def is_demo(self) -> bool:
        return self.provider == "replay"

    @property
    def score_text(self) -> str:
        """Tỷ số theo kiểu đội nhà trước, vd. 'Atlético 2-0 MU'."""
        if self.mu_goals is None or self.opp_goals is None:
            return ""
        if self.home:
            return f"MU {self.mu_goals}-{self.opp_goals} {self.opponent}"
        return f"{self.opponent} {self.opp_goals}-{self.mu_goals} MU"


class Cave(Base):
    """Một lần hang mở: từ trận thua đến trận thắng kế tiếp."""

    __tablename__ = "caves"

    id: Mapped[int] = mapped_column(primary_key=True)
    group_id: Mapped[int] = mapped_column(ForeignKey("groups.id"), index=True)
    status: Mapped[str] = mapped_column(String(6))  # OPEN | CLOSED
    opened_at: Mapped[datetime] = mapped_column(DateTime)
    opened_by_fixture_id: Mapped[int | None] = mapped_column(ForeignKey("fixtures.id"))
    closed_at: Mapped[datetime | None] = mapped_column(DateTime)
    closed_by_fixture_id: Mapped[int | None] = mapped_column(ForeignKey("fixtures.id"))


class Membership(Base):
    """Một lần một người ở trong hang. Ra rồi vào lại tạo dòng mới."""

    __tablename__ = "memberships"

    id: Mapped[int] = mapped_column(primary_key=True)
    cave_id: Mapped[int] = mapped_column(ForeignKey("caves.id"), index=True)
    user_id: Mapped[int] = mapped_column(ForeignKey("users.id"), index=True)
    entered_at: Mapped[datetime] = mapped_column(DateTime)
    left_at: Mapped[datetime | None] = mapped_column(DateTime)
    left_reason: Mapped[str | None] = mapped_column(String(4))  # self | win


class LightWindow(Base):
    """Khoảng sáng đèn sau một trận không thắng khi hang đang mở."""

    __tablename__ = "light_windows"
    __table_args__ = (UniqueConstraint("cave_id", "fixture_id"),)

    id: Mapped[int] = mapped_column(primary_key=True)
    cave_id: Mapped[int] = mapped_column(ForeignKey("caves.id"), index=True)
    fixture_id: Mapped[int] = mapped_column(ForeignKey("fixtures.id"))
    starts_at: Mapped[datetime] = mapped_column(DateTime)
    ends_at: Mapped[datetime] = mapped_column(DateTime)


class Presence(Base):
    __tablename__ = "presence"
    __table_args__ = (UniqueConstraint("user_id", "fixture_id"),)

    id: Mapped[int] = mapped_column(primary_key=True)
    user_id: Mapped[int] = mapped_column(ForeignKey("users.id"))
    fixture_id: Mapped[int] = mapped_column(ForeignKey("fixtures.id"))
    state: Mapped[str] = mapped_column(String(6))  # awake | asleep | busy
    reported_at: Mapped[datetime] = mapped_column(DateTime)


class PatLine(Base):
    """Câu vỗ vai dùng cho một trận (AI soạn hoặc lấy từ kho dự phòng)."""

    __tablename__ = "pat_lines"

    id: Mapped[int] = mapped_column(primary_key=True)
    fixture_id: Mapped[int] = mapped_column(ForeignKey("fixtures.id"), index=True)
    text: Mapped[str] = mapped_column(String(200))
    source: Mapped[str] = mapped_column(String(4))  # ai | bank


class Pat(Base):
    __tablename__ = "pats"

    id: Mapped[int] = mapped_column(primary_key=True)
    window_id: Mapped[int] = mapped_column(ForeignKey("light_windows.id"), index=True)
    from_user_id: Mapped[int] = mapped_column(ForeignKey("users.id"))
    line_id: Mapped[int] = mapped_column(ForeignKey("pat_lines.id"))
    sent_at: Mapped[datetime] = mapped_column(DateTime)


class Invite(Base):
    __tablename__ = "invites"
    __table_args__ = (UniqueConstraint("window_id", "from_user_id"),)

    id: Mapped[int] = mapped_column(primary_key=True)
    window_id: Mapped[int] = mapped_column(ForeignKey("light_windows.id"))
    from_user_id: Mapped[int] = mapped_column(ForeignKey("users.id"))
    sent_at: Mapped[datetime] = mapped_column(DateTime)
    recipients: Mapped[int] = mapped_column(Integer, default=0)


class TelemetryEvent(Base):
    __tablename__ = "telemetry"

    id: Mapped[int] = mapped_column(primary_key=True)
    user_id: Mapped[int | None] = mapped_column(ForeignKey("users.id"))
    type: Mapped[str] = mapped_column(String(40), index=True)
    ts: Mapped[datetime] = mapped_column(DateTime)
    props: Mapped[str] = mapped_column(Text, default="{}")


class ApiUsage(Base):
    """Đếm request gọi nhà cung cấp dữ liệu trận theo ngày UTC."""

    __tablename__ = "api_usage"
    __table_args__ = (UniqueConstraint("provider", "day"),)

    id: Mapped[int] = mapped_column(primary_key=True)
    provider: Mapped[str] = mapped_column(String(20))
    day: Mapped[str] = mapped_column(String(10))
    count: Mapped[int] = mapped_column(Integer, default=0)


class KeyValue(Base):
    __tablename__ = "kv"

    key: Mapped[str] = mapped_column(String(40), primary_key=True)
    value: Mapped[str] = mapped_column(Text)


def normalize_database_url(database_url: str) -> str:
    """Supabase/Neon đưa chuỗi dạng postgresql://… → dùng driver psycopg 3."""
    for prefix in ("postgres://", "postgresql://"):
        if database_url.startswith(prefix):
            return "postgresql+psycopg://" + database_url[len(prefix):]
    return database_url


def make_sessionmaker(database_url: str) -> sessionmaker:
    database_url = normalize_database_url(database_url)
    kwargs = {}
    if not database_url.startswith("sqlite"):
        kwargs["pool_pre_ping"] = True  # pooler có thể cắt kết nối rảnh
    if database_url.startswith("sqlite"):
        kwargs["connect_args"] = {"check_same_thread": False}
        if database_url in ("sqlite://", "sqlite:///:memory:"):
            from sqlalchemy.pool import StaticPool

            kwargs["poolclass"] = StaticPool
    engine = create_engine(database_url, **kwargs)
    Base.metadata.create_all(engine)
    return sessionmaker(bind=engine, expire_on_commit=False)
