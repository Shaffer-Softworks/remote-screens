"""One Tessera call's audio: WebRTC Opus on stdin/stdout JSON, 16 kHz PCM in 20 ms parts.

The add-on speaks the commands. aiortc is imported only when a call starts, so the framing
helper can be tested without it. There is no STUN server; both ends are on the home network.
"""

from __future__ import annotations

import asyncio
import base64
import json
import sys

RATE = 16000
PART = 640  # 20 ms of 16-bit mono
GATHER_SECONDS = 1.5


def take_parts(spare: bytes, data: bytes, part: int = PART) -> tuple[list[bytes], bytes]:
    """Whole parts of `part` bytes, and the fragment that is still short of one."""
    combined = spare + data
    whole = len(combined) - len(combined) % part
    parts = [combined[index : index + part] for index in range(0, whole, part)]
    return parts, combined[whole:]


def emit(payload: dict) -> None:
    sys.stdout.write(json.dumps(payload) + "\n")
    sys.stdout.flush()


async def read_commands(queue: asyncio.Queue) -> None:
    loop = asyncio.get_running_loop()
    while True:
        line = await loop.run_in_executor(None, sys.stdin.readline)
        if line == "":
            await queue.put(None)
            return
        text = line.strip()
        if text == "":
            continue
        try:
            await queue.put(json.loads(text))
        except json.JSONDecodeError:
            emit({"failed": "unreadable command"})


def pcm_of(frame) -> bytes:
    plane = frame.planes[0]
    raw = plane.to_bytes() if hasattr(plane, "to_bytes") else bytes(plane)
    return raw


def resampled(resampler, frame) -> list:
    out = resampler.resample(frame)
    if out is None:
        return []
    if isinstance(out, list):
        return out
    return [out]


class PcmTrack:
    """Plays the Tessera screen's PCM into the WebRTC audio track."""

    def __init__(self) -> None:
        from aiortc import MediaStreamTrack

        class Track(MediaStreamTrack):
            kind = "audio"

            def __init__(self) -> None:
                super().__init__()
                self.queue: asyncio.Queue = asyncio.Queue()
                self.pts = 0

            async def recv(self):
                import av
                from fractions import Fraction

                pcm = await self.queue.get()
                samples = len(pcm) // 2
                frame = av.AudioFrame(format="s16", layout="mono", samples=samples)
                frame.planes[0].update(pcm)
                frame.sample_rate = RATE
                frame.pts = self.pts
                frame.time_base = Fraction(1, RATE)
                self.pts += samples
                return frame

        self.track = Track()

    async def push(self, pcm: bytes) -> None:
        await self.track.queue.put(pcm)


async def pump_microphone(track, resampler) -> None:
    spare = b""
    while True:
        frame = await track.recv()
        for item in resampled(resampler, frame):
            parts, spare = take_parts(spare, pcm_of(item))
            for part in parts:
                emit({"pcm": base64.b64encode(part).decode("ascii")})


async def gather(pc) -> None:
    if pc.iceGatheringState == "complete":
        return
    done = asyncio.Event()

    @pc.on("icegatheringstatechange")
    def gathering_changed() -> None:
        if pc.iceGatheringState == "complete":
            done.set()

    try:
        await asyncio.wait_for(done.wait(), GATHER_SECONDS)
    except asyncio.TimeoutError:
        return


def candidate_of(payload: dict):
    from aiortc.sdp import candidate_from_sdp

    raw = str(payload.get("candidate") or "")
    body = raw.split(":", 1)[1] if raw.startswith("candidate:") else raw
    candidate = candidate_from_sdp(body)
    candidate.sdpMid = payload.get("sdpMid")
    candidate.sdpMLineIndex = payload.get("sdpMLineIndex") or 0
    return candidate


async def session(first: dict, commands: asyncio.Queue) -> None:
    import av
    from aiortc import RTCConfiguration, RTCPeerConnection, RTCSessionDescription

    pc = RTCPeerConnection(RTCConfiguration(iceServers=[]))
    played = PcmTrack()
    pc.addTrack(played.track)
    resampler = av.AudioResampler(format="s16", layout="mono", rate=RATE)
    pending: list[dict] = []
    closing = False

    @pc.on("track")
    def on_track(track) -> None:
        if track.kind == "audio":
            asyncio.ensure_future(pump_microphone(track, resampler))

    @pc.on("connectionstatechange")
    def connection_changed() -> None:
        if not closing and pc.connectionState in ("failed", "closed"):
            emit({"failed": pc.connectionState})

    async def add_candidate(payload: dict) -> None:
        if pc.remoteDescription is None:
            pending.append(payload)
            return
        await pc.addIceCandidate(candidate_of(payload))

    async def flush_candidates() -> None:
        waiting = pending[:]
        pending.clear()
        for payload in waiting:
            await pc.addIceCandidate(candidate_of(payload))

    async def offer() -> None:
        await pc.setLocalDescription(await pc.createOffer())
        await gather(pc)
        emit({"sdp": pc.localDescription.sdp})

    async def answer(sdp: str) -> None:
        await pc.setRemoteDescription(RTCSessionDescription(sdp, "offer"))
        await flush_candidates()
        await pc.setLocalDescription(await pc.createAnswer())
        await gather(pc)
        emit({"sdp": pc.localDescription.sdp})

    async def accept(sdp: str) -> None:
        await pc.setRemoteDescription(RTCSessionDescription(sdp, "answer"))
        await flush_candidates()

    async def handle(command: dict) -> bool:
        kind = command.get("cmd")
        if kind == "hangup":
            return False
        if kind == "offer":
            await offer()
        elif kind == "answer":
            await answer(str(command.get("sdp") or ""))
        elif kind == "accept":
            await accept(str(command.get("sdp") or ""))
        elif kind == "candidate":
            await add_candidate(command)
        elif kind == "pcm":
            raw = base64.b64decode(str(command.get("data") or ""))
            parts, _rest = take_parts(b"", raw)
            for part in parts:
                await played.push(part)
        return True

    try:
        if not await handle(first):
            return
        while True:
            command = await commands.get()
            if command is None or not await handle(command):
                return
    finally:
        closing = True
        await pc.close()


async def main() -> None:
    commands: asyncio.Queue = asyncio.Queue()
    reader = asyncio.create_task(read_commands(commands))
    first = await commands.get()
    if first is None:
        return
    try:
        await session(first, commands)
    except Exception as error:  # noqa: BLE001 - one line back to the add-on, then the call ends
        emit({"failed": str(error) or "media failed"})
    finally:
        reader.cancel()


if __name__ == "__main__":
    asyncio.run(main())
