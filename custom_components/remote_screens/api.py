"""Client for the add-on's integration API (`/integration`, on the players' port)."""

from __future__ import annotations

import asyncio
from typing import Any

import aiohttp

TIMEOUT = aiohttp.ClientTimeout(total=10)


class RemoteScreensError(Exception):
    """The add-on could not be reached or answered with an error."""


class RemoteScreensAuthError(RemoteScreensError):
    """The add-on refused the API key."""


class RemoteScreensNotFoundError(RemoteScreensError):
    """The add-on has no such path, such as an older add-on without `/players`."""


class RemoteScreensClient:
    """Talks to one Remote Screens add-on."""

    def __init__(self, session: aiohttp.ClientSession, host: str, port: int, api_key: str) -> None:
        self._session = session
        self._base = f"http://{host}:{port}/integration"
        self._headers = {"Authorization": f"Bearer {api_key}"}

    async def screens(self) -> list[dict[str, Any]]:
        """Every screen with its settings, page titles and players."""
        return await self._request("GET", "/screens")

    async def players(self) -> list[dict[str, Any]]:
        """Every tablet, with or without a screen, and its sensors while it is online."""
        return await self._request("GET", "/players")

    async def set_settings(self, screen_id: str, patch: dict[str, Any]) -> None:
        """Changes some of a screen's settings; the rest stay as they are."""
        await self._request("POST", f"/screens/{screen_id}/settings", patch)

    async def show_alert(self, alert: dict[str, Any]) -> None:
        await self._request("POST", "/alert", alert)

    async def open_page(self, page: str, targets: list[str]) -> None:
        await self._request("POST", "/open_page", {"page": page, "targets": targets})

    async def wake(self, targets: list[str]) -> None:
        await self._request("POST", "/wake", {"targets": targets})

    async def close(self, targets: list[str]) -> None:
        await self._request("POST", "/close", {"targets": targets})

    async def _request(self, method: str, path: str, body: dict[str, Any] | None = None) -> Any:
        try:
            async with self._session.request(
                method, self._base + path, json=body, headers=self._headers, timeout=TIMEOUT
            ) as r:
                if r.status in (401, 403):
                    raise RemoteScreensAuthError("The add-on refused the API key")
                if r.status == 404:
                    raise RemoteScreensNotFoundError(f"Not found: {path}")
                if r.status >= 400:
                    raise RemoteScreensError(f"{r.status}: {(await r.text())[:200]}")
                if r.status == 204 or r.content_length == 0:
                    return None
                return await r.json()
        except (aiohttp.ClientError, asyncio.TimeoutError) as err:
            raise RemoteScreensError(f"Cannot reach the add-on: {err}") from err
