"""Hội anh em: tạo bằng mã mời, hai bên cùng đồng ý mới vào."""

from __future__ import annotations

import secrets

from sqlalchemy import select
from sqlalchemy.orm import Session

from ..clock import Clock
from ..models import Group, GroupMember, Membership, User
from .cave import CaveError, approved_members, open_cave, record

MAX_MEMBERS = 10
_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"  # bỏ O/0, I/1 cho dễ đọc


def _new_code(db: Session) -> str:
    while True:
        code = "".join(secrets.choice(_ALPHABET) for _ in range(6))
        if db.scalar(select(Group).where(Group.invite_code == code)) is None:
            return code


def create_group(db: Session, clock: Clock, owner: User, name: str) -> Group:
    group = Group(name=name.strip()[:60] or "Hội anh em", invite_code=_new_code(db), owner_id=owner.id,
                  created_at=clock.now())
    db.add(group)
    db.flush()
    db.add(GroupMember(group_id=group.id, user_id=owner.id, status="approved", joined_at=clock.now()))
    record(db, clock, "group_created", owner.id, group_id=group.id)
    db.commit()
    return group


def request_join(db: Session, clock: Clock, user: User, code: str) -> GroupMember:
    group = db.scalar(select(Group).where(Group.invite_code == code.strip().upper()))
    if group is None:
        raise CaveError("bad_code", 404)
    existing = db.scalar(select(GroupMember).where(GroupMember.group_id == group.id, GroupMember.user_id == user.id))
    if existing:
        return existing
    member = GroupMember(group_id=group.id, user_id=user.id, status="pending", joined_at=clock.now())
    db.add(member)
    db.commit()
    return member


def approve(db: Session, owner: User, group_id: int, user_id: int) -> None:
    group = db.get(Group, group_id)
    if group is None or group.owner_id != owner.id:
        raise CaveError("not_owner", 403)
    member = db.scalar(select(GroupMember).where(GroupMember.group_id == group_id, GroupMember.user_id == user_id))
    if member is None:
        raise CaveError("no_request", 404)
    if member.status != "approved":
        if len(approved_members(db, group_id)) >= MAX_MEMBERS:
            raise CaveError("group_full", 409)
        member.status = "approved"
        db.commit()


def leave_group(db: Session, clock: Clock, user: User, group_id: int) -> None:
    member = db.scalar(select(GroupMember).where(GroupMember.group_id == group_id, GroupMember.user_id == user.id))
    if member is None:
        return
    cave = open_cave(db, group_id)
    if cave:
        for m in db.scalars(select(Membership).where(
            Membership.cave_id == cave.id, Membership.user_id == user.id, Membership.left_at.is_(None)
        )):
            m.left_at, m.left_reason = clock.now(), "self"
    db.delete(member)
    db.commit()


def groups_for(db: Session, user: User) -> list[dict]:
    rows = db.scalars(select(GroupMember).where(GroupMember.user_id == user.id))
    result = []
    for row in rows:
        group = db.get(Group, row.group_id)
        is_owner = group.owner_id == user.id
        item = {"id": group.id, "name": group.name, "status": row.status, "is_owner": is_owner}
        if row.status == "approved":
            cave = open_cave(db, group.id)
            item["members"] = [{"id": u.id, "nickname": u.nickname} for u in approved_members(db, group.id)]
            item["cave"] = {"id": cave.id, "status": cave.status} if cave else None
            if is_owner:
                item["invite_code"] = group.invite_code
                pending = db.scalars(select(GroupMember).where(
                    GroupMember.group_id == group.id, GroupMember.status == "pending"))
                item["pending"] = [{"id": p.user_id, "nickname": db.get(User, p.user_id).nickname} for p in pending]
        result.append(item)
    return result
