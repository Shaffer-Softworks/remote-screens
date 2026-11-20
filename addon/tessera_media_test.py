"""Framing for Tessera's 20 ms PCM parts. Does not import aiortc."""

import unittest

from tessera_media import PART, take_parts


class TakePartsTest(unittest.TestCase):
    def test_a_short_chunk_waits_for_a_whole_part(self) -> None:
        parts, spare = take_parts(b"", b"\x00" * (PART - 1))
        self.assertEqual(parts, [])
        self.assertEqual(len(spare), PART - 1)

    def test_a_remainder_completes_the_next_part(self) -> None:
        first, spare = take_parts(b"", b"\x01" * 100)
        parts, left = take_parts(spare, b"\x02" * (PART - 100 + 40))
        self.assertEqual(first, [])
        self.assertEqual(len(parts), 1)
        self.assertEqual(len(parts[0]), PART)
        self.assertEqual(len(left), 40)

    def test_an_exact_part_leaves_nothing(self) -> None:
        parts, spare = take_parts(b"", b"\x03" * PART)
        self.assertEqual(len(parts), 1)
        self.assertEqual(spare, b"")


if __name__ == "__main__":
    unittest.main()
