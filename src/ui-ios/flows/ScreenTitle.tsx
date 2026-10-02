/**
 * LT-1 fallback: the screen's own large title when the native header shows only
 * Back. 18 pt below the header like the setup (Screen pads 16, plus 2), 14 pt to
 * the first block through screenRhythm. It must be the content's first child so
 * it is first in reading order. No manual accessibility focus: the header still
 * announces the screen's name on entry, so focusing here would repeat it (Iris);
 * the next swipe lands on this title.
 */
import { Text } from "../components";
import { useTokens } from "../theme";

export function ScreenTitle({ children }: { children: string }) {
  const tokens = useTokens();
  return (
    <Text variant="title1" header style={{ marginTop: tokens.space.xxs }}>
      {children}
    </Text>
  );
}
