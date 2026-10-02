/**
 * Dev-only catalog of every primitive in its states, for visual QA (Prism,
 * Piston screenshots). Reached from the drawer in __DEV__ builds only.
 * Strings are intentionally not translated: this screen never ships to users.
 */
import React, { useState } from "react";
import { View } from "react-native";
import type { Appearance, FontScale, PaletteChoice } from "../../models/settings";
import {
  Badge,
  Banner,
  Button,
  Card,
  Chip,
  EmptyState,
  Icon,
  IconButton,
  ListRow,
  MetaLine,
  OfflineSeal,
  OptionCard,
  Progress,
  Screen,
  Section,
  SegmentedControl,
  Sheet,
  Skeleton,
  Stat,
  Stepper,
  Switch,
  Text,
  TextField,
  useAnnounce,
  useToast,
} from "../components";
import { getPalette, useTheme } from "../theme";
import type { TextVariant } from "../theme";

const VARIANTS: TextVariant[] = ["display", "title1", "title2", "title3", "headline", "button", "body", "callout", "subhead", "footnote", "caption", "label", "mono", "code"];

function Group({ title, children }: { title: string; children: React.ReactNode }) {
  const { tokens: t } = useTheme();
  return (
    <View style={{ gap: t.space.md }}>
      <Text variant="label" color="tertiary" header>
        {title}
      </Text>
      {children}
    </View>
  );
}

function Swatches() {
  const { tokens: t, scheme, palette: paletteId } = useTheme();
  const palette = getPalette(paletteId, scheme);
  const entries = Object.entries(palette).filter((e): e is [string, string] => typeof e[1] === "string");
  return (
    <View style={{ flexDirection: "row", flexWrap: "wrap", gap: t.space.sm }}>
      {entries.map(([name, hex]) => (
        <View key={name} style={{ width: 96, gap: 2 }}>
          <View
            style={{
              height: 40,
              borderRadius: t.radius.sm,
              backgroundColor: hex,
              borderWidth: t.size.hairline,
              borderColor: t.color.line.hairline,
            }}
          />
          <Text variant="caption">{name}</Text>
          <Text variant="caption" color="tertiary" numeric>
            {hex}
          </Text>
        </View>
      ))}
    </View>
  );
}

export function ComponentCatalogScreen() {
  const { tokens: t, appearance, setAppearance, fontScale, setFontScale, palette, setPalette } = useTheme();
  const toast = useToast();
  const announce = useAnnounce();
  const [on, setOn] = useState(true);
  const [seg, setSeg] = useState<"fast" | "balanced" | "deep">("balanced");
  const [sheet, setSheet] = useState(false);
  const [text, setText] = useState("");
  const [chip, setChip] = useState(true);
  const [tier, setTier] = useState<"essential" | "encyclopedia">("essential");
  const [lang, setLang] = useState<"en" | "pt">("en");

  return (
    <Screen ambient>
      <Group title="Theme">
        <SegmentedControl<PaletteChoice>
          label="Palette"
          value={palette}
          onChange={setPalette}
          options={[
            { value: "fogueira", label: "Fogueira", icon: "sun" },
            { value: "luar", label: "Luar", icon: "moon" },
          ]}
        />
        <SegmentedControl<Appearance>
          label="Appearance"
          value={appearance}
          onChange={setAppearance}
          options={[
            { value: "system", label: "System", icon: "smartphone" },
            { value: "light", label: "Light", icon: "sun" },
            { value: "dark", label: "Dark", icon: "moon" },
          ]}
        />
        <SegmentedControl<FontScale>
          label="Text size"
          value={fontScale}
          onChange={setFontScale}
          options={[
            { value: "compact", label: "A−" },
            { value: "standard", label: "A" },
            { value: "large", label: "A+" },
          ]}
        />
      </Group>

      <Group title="Type">
        {VARIANTS.map((v) => (
          <Text key={v} variant={v}>
            {v} · Qualidade por segundo 123
          </Text>
        ))}
        <Text variant="body" color="secondary">
          secondary text
        </Text>
        <Text variant="body" color="tertiary">
          tertiary text (AA)
        </Text>
        <Text variant="body" color="accent">
          accent text
        </Text>
        <Text variant="body" color="field">
          field text (provenance)
        </Text>
      </Group>

      <Group title="Color">
        <Swatches />
      </Group>

      <Group title="Buttons">
        <Button label="Primary action" onPress={() => {}} />
        <Button label="Secondary" variant="secondary" icon="download" onPress={() => {}} />
        <Button label="Outline" variant="outline" onPress={() => {}} />
        <Button label="Ghost" variant="ghost" onPress={() => {}} />
        <Button label="Delete model" variant="destructive" icon="trash-2" onPress={() => {}} />
        <Button label="Loading" loading onPress={() => {}} />
        <Button label="Disabled" disabled onPress={() => {}} />
        <View style={{ flexDirection: "row", gap: t.space.sm }}>
          <Button label="Small" size="sm" onPress={() => {}} />
          <Button label="Small secondary" size="sm" variant="secondary" onPress={() => {}} />
        </View>
        <View style={{ flexDirection: "row", gap: t.space.sm, alignItems: "center" }}>
          <IconButton icon="menu" label="Open menu" onPress={() => {}} />
          <IconButton icon="mic" label="Voice input" variant="tonal" onPress={() => {}} />
          <IconButton icon="arrow-up" label="Send" variant="filled" onPress={() => {}} />
          <IconButton icon="square" label="Stop" variant="stop" size="lg" onPress={() => {}} />
          <IconButton icon="square" label="Stopping" variant="stop" size="lg" busy onPress={() => {}} />
          <IconButton icon="square" label="Stop" size="sm" onPress={() => {}} />
          <IconButton icon="bookmark" label="Selected" selected onPress={() => {}} />
        </View>
      </Group>

      <Group title="Chips and badges">
        <View style={{ flexDirection: "row", flexWrap: "wrap", gap: t.space.sm }}>
          <Chip label="Deep research" icon="layers" selected={chip} onPress={() => setChip(!chip)} />
          <Chip label="Wikipedia" icon="book" tone="field" onPress={() => {}} />
          <Chip label="Small" size="sm" onPress={() => {}} />
        </View>
        <Text variant="body">
          Canberra is the capital <Chip label="1" size="inline" tone="field" onPress={() => {}} accessibilityLabel="Source 1" /> of Australia.
        </Text>
        <View style={{ flexDirection: "row", flexWrap: "wrap", gap: t.space.sm }}>
          <Badge label="Offline" tone="field" icon="wifi-off" />
          <Badge label="Installed" tone="success" icon="check" />
          <Badge label="Downloading" tone="info" />
          <Badge label="Low storage" tone="warning" />
          <Badge label="Failed" tone="danger" />
          <Badge label="In use" tone="accent" emphasis="solid" />
          <Badge label="Neutral" />
        </View>
        <View style={{ flexDirection: "row", flexWrap: "wrap", gap: t.space.sm }}>
          <Badge label="Active" tone="accent" emphasis="solid" icon="check" />
          <Badge label="Cached" tone="field" icon="database" />
          <Badge label="Downloading" tone="field" emphasis="outline" icon="loader" />
          <Badge label="Not on disk" emphasis="outline" />
        </View>
        <View style={{ gap: t.space.sm }}>
          <Badge label="Fits this device (measured)" tone="success" dot caps={false} />
          <Badge label="Needs 12 GB, device has 8 GB" tone="warning" dot caps={false} />
          <Badge label="Exceeds device RAM" tone="danger" dot caps={false} />
        </View>
      </Group>

      <Group title="Offline seal">
        <OfflineSeal label="Offline" />
        <OfflineSeal label="Offline" variant="moon" />
        <OfflineSeal label="Offline" sublabel="Local model" variant="card" />
      </Group>

      <Group title="Banners">
        <Banner tone="field" title="Running offline" message="No network used. Answers come from on-device models and your knowledge packs." />
        <Banner tone="info" message="Indexing 51,300 articles. You can keep chatting." />
        <Banner tone="warning" message="Only 1.2 GB free. The next model needs 2.4 GB." actionLabel="Manage storage" onAction={() => {}} />
        <Banner tone="danger" title="Model failed to load" message="Out of memory while loading Qwen2.5 3B." actionLabel="Use a smaller model" onAction={() => {}} onDismiss={() => {}} />
        <Banner tone="success" message="Checksum verified." />
      </Group>

      <Group title="Choices and figures">
        <Stepper steps={["Hardware", "Model", "Install", "Index"]} current={1} accessibilityLabel="Step 2 of 4: Model" />
        <OptionCard
          title="Essential"
          selected={tier === "essential"}
          onPress={() => setTier("essential")}
          trailing="978 MB"
          description="Answer and search models, plus the core topic collections."
          meta={["~4 min at 5 MB/s", "978 MB on disk"]}
        />
        <OptionCard
          title="Encyclopedia"
          badge={<Badge label="Recommended" tone="accent" />}
          selected={tier === "encyclopedia"}
          onPress={() => setTier("encyclopedia")}
          trailing="1.1 GB"
          description="Essential plus the Vital Articles pack."
          meta={["~4 min at 5 MB/s", "1.1 GB on disk"]}
        />
        <View style={{ flexDirection: "row", gap: t.space.md }}>
          {(["en", "pt"] as const).map((l) => (
            <View key={l} style={{ flex: 1 }}>
              <OptionCard
                title={l === "en" ? "English" : "Português"}
                indicator="check"
                selected={lang === l}
                onPress={() => setLang(l)}
              />
            </View>
          ))}
        </View>
        <Card>
          <View style={{ gap: t.space.sm }}>
            <Stat label="Downloading" value="62" unit="%" size="lg" />
            <Progress value={0.62} label="Download" />
            <MetaLine items={["611 / 986 MB", "2 min left"]} />
          </View>
        </Card>
        <MetaLine items={["1.4 s", "16 tok/s", false]} />
      </Group>

      <Group title="Lists">
        <Section title="Answers" footer="Section footer explains the effect of the settings above.">
          <ListRow title="Tone" value="Succinct" icon="message-circle" onPress={() => {}} />
          <ListRow title="Deep research" subtitle="Whole row is the switch" icon="layers" switch={{ value: on, onValueChange: setOn }} />
          <ListRow title="Standalone switch" trailing={<Switch label="Standalone switch" value={on} onValueChange={setOn} />} accessibilityLabel="Standalone switch row" />
          <ListRow title="Models" subtitle="Qwen2.5 1.5B in use · 3 installed" icon="cpu" onPress={() => {}} />
          <ListRow title="Details" expanded={on} onPress={() => setOn(!on)} />
          <ListRow title="Erase all data" icon="trash-2" destructive onPress={() => {}} />
        </Section>
      </Group>

      <Group title="Inputs">
        <TextField label="Collection name" placeholder="e.g. Field notes" value={text} onChangeText={setText} helper="Shown in the Knowledge screen." />
        <TextField label="With error" value="bad value" error="Name already in use." onChangeText={() => {}} />
        <TextField accessibilityLabel="Message" placeholder="Ask something…" autoGrow value={text} onChangeText={setText} trailing={<IconButton icon="arrow-up" label="Send" variant="filled" size="sm" onPress={() => {}} />} />
        <SegmentedControl
          label="Answer depth"
          value={seg}
          onChange={setSeg}
          options={[
            { value: "fast", label: "Fast" },
            { value: "balanced", label: "Balanced" },
            { value: "deep", label: "Deep" },
          ]}
        />
      </Group>

      <Group title="Progress and loading">
        <Progress label="Downloading model" value={0.34} valueText="340 of 1,020 MB" />
        <Progress label="Verifying" tone="field" />
        <Card>
          <View style={{ gap: t.space.sm }}>
            <Skeleton width="60%" height={18} />
            <Skeleton />
            <Skeleton width="80%" />
          </View>
        </Card>
      </Group>

      <Group title="Cards">
        <Card>
          <View style={{ flexDirection: "row", gap: t.space.md, alignItems: "center" }}>
            <Icon name="cpu" color={t.color.accent.text} />
            <View style={{ flex: 1 }}>
              <Text variant="headline">Qwen2.5 1.5B</Text>
              <Text variant="footnote" color="secondary" numeric>
                1.02 GB · 16 tok/s on this device
              </Text>
            </View>
            <Badge label="In use" tone="accent" />
          </View>
        </Card>
        <Card level={2} onPress={() => {}} accessibilityLabel="Pressable card">
          <Text variant="body">Pressable card, level 2</Text>
        </Card>
      </Group>

      <Group title="Empty and error">
        <Card padding="none">
          <EmptyState icon="book-open" title="No documents yet" body="Import PDFs or text files. They stay on this device." actionLabel="Import files" onAction={() => {}} />
        </Card>
        <Card padding="none">
          <EmptyState tone="error" title="Couldn't read that file" body="The PDF has no text layer." actionLabel="Try another file" onAction={() => {}} secondaryLabel="Learn more" onSecondary={() => {}} />
        </Card>
      </Group>

      <Group title="Overlays">
        <Button label="Open sheet" variant="secondary" onPress={() => setSheet(true)} />
        <Button label="Show toast with undo" variant="secondary" onPress={() => toast({ message: "Conversation deleted", icon: "trash-2", actionLabel: "Undo", onAction: () => {} })} />
        <Button label="Show error toast" variant="secondary" onPress={() => toast({ message: "Couldn't save the file.", tone: "danger" })} />
        <Button label="Announce (screen reader)" variant="ghost" onPress={() => announce("Answer ready, 3 sources")} />
      </Group>

      <Sheet
        visible={sheet}
        onClose={() => setSheet(false)}
        title="Delete this model?"
        description="Qwen2.5 3B (2.4 GB) will be removed. You can download it again later."
        footer={
          <>
            <Button label="Cancel" variant="ghost" fullWidth onPress={() => setSheet(false)} />
            <Button label="Delete model" variant="destructive" icon="trash-2" fullWidth onPress={() => setSheet(false)} />
          </>
        }
      />
    </Screen>
  );
}
