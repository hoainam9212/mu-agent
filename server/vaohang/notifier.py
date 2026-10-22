"""Gửi FCM data message tới app. App tự quyết hiện thông báo ngay hay để sáng (luật thức/ngủ trên máy)."""

from __future__ import annotations

import json
import logging
from collections import deque
from typing import Protocol

from .models import User

log = logging.getLogger(__name__)


def _stringify(data: dict) -> dict[str, str]:
    # FCM data message chỉ nhận giá trị chuỗi.
    return {k: v if isinstance(v, str) else json.dumps(v, ensure_ascii=False) for k, v in data.items()}


class Notifier(Protocol):
    def send(self, user: User, data: dict) -> None: ...


class OutboxNotifier:
    """Không gửi thật; giữ lại tin gần nhất để xem qua admin API (dev, demo, test)."""

    def __init__(self, maxlen: int = 500):
        self.sent: deque[tuple[int, dict[str, str]]] = deque(maxlen=maxlen)

    def send(self, user: User, data: dict) -> None:
        payload = _stringify(data)
        self.sent.append((user.id, payload))
        log.info("notify user=%s %s", user.id, payload)

    def for_user(self, user_id: int) -> list[dict[str, str]]:
        return [payload for uid, payload in self.sent if uid == user_id]


class FcmNotifier:
    """Gửi qua Firebase Cloud Messaging (cần GOOGLE_APPLICATION_CREDENTIALS)."""

    def __init__(self):
        import firebase_admin
        from firebase_admin import messaging

        if not firebase_admin._apps:
            firebase_admin.initialize_app()
        self._messaging = messaging

    def send(self, user: User, data: dict) -> None:
        if not user.fcm_token:
            return
        m = self._messaging
        message = m.Message(
            token=user.fcm_token,
            data=_stringify(data),
            android=m.AndroidConfig(priority="high", ttl=6 * 3600),
        )
        try:
            m.send(message)
        except Exception:  # một máy lỗi không được làm hỏng cả lượt gửi
            log.exception("FCM send failed for user=%s", user.id)
