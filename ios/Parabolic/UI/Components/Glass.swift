import SwiftUI

// MARK: - Ambient backdrop

/// The plain system background behind every screen: light gray in light
/// mode, black in dark mode. The glass cards sit on top of it.
struct AmbientBackground: View {
    var body: some View {
        Color(uiColor: .systemGroupedBackground)
            .ignoresSafeArea()
    }
}

// MARK: - Cards and sections

extension View {
    /// The standard Liquid Glass card used throughout the app.
    func glassCard(cornerRadius: CGFloat = 28, padding: CGFloat = 16, interactive: Bool = false) -> some View {
        self
            .padding(padding)
            .frame(maxWidth: .infinity, alignment: .leading)
            .glassEffect(interactive ? .regular.interactive() : .regular, in: .rect(cornerRadius: cornerRadius))
    }
}

/// A titled group of rows on a single glass card.
struct GlassSection<Content: View>: View {
    let title: String
    var footer: String?
    @ViewBuilder var content: Content

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(title)
                .font(.footnote.weight(.semibold))
                .foregroundStyle(.secondary)
                .textCase(.uppercase)
                .padding(.horizontal, 20)
            VStack(alignment: .leading, spacing: 0) {
                content
            }
            .glassCard(cornerRadius: 26, padding: 6)
            if let footer {
                Text(footer)
                    .font(.footnote)
                    .foregroundStyle(.secondary)
                    .padding(.horizontal, 20)
            }
        }
    }
}

/// One row inside a `GlassSection`: tinted icon, title, optional detail, trailing control.
struct GlassRow<Trailing: View>: View {
    let symbol: String
    let tint: Color
    let title: String
    var detail: String?
    @ViewBuilder var trailing: Trailing

    var body: some View {
        HStack(spacing: 14) {
            Image(systemName: symbol)
                .font(.system(size: 15, weight: .semibold))
                .foregroundStyle(.white)
                .frame(width: 32, height: 32)
                .background(tint, in: .rect(cornerRadius: 9))
            VStack(alignment: .leading, spacing: 2) {
                Text(title)
                if let detail {
                    Text(detail)
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                }
            }
            Spacer(minLength: 8)
            trailing
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 10)
        .frame(minHeight: 52)
    }
}

extension GlassRow where Trailing == EmptyView {
    init(symbol: String, tint: Color, title: String, detail: String? = nil) {
        self.init(symbol: symbol, tint: tint, title: title, detail: detail) { EmptyView() }
    }
}

struct GlassDivider: View {
    var body: some View {
        Divider().padding(.leading, 58)
    }
}

// MARK: - Morphing choice bar

/// A segmented choice where the selected pill is a tinted Liquid Glass shape
/// that morphs from option to option.
struct GlassChoiceBar<Option: Hashable & Identifiable>: View {
    let options: [Option]
    @Binding var selection: Option
    let label: (Option) -> String
    var symbol: ((Option) -> String?)?

    @Namespace private var namespace

    var body: some View {
        GlassEffectContainer(spacing: 8) {
            HStack(spacing: 4) {
                ForEach(options) { option in
                    let isSelected = option == selection
                    Button {
                        withAnimation(.bouncy(duration: 0.35)) { selection = option }
                    } label: {
                        HStack(spacing: 6) {
                            if let name = symbol?(option) {
                                Image(systemName: name)
                            }
                            Text(label(option))
                                .lineLimit(1)
                                .minimumScaleFactor(0.8)
                        }
                        .font(.subheadline.weight(isSelected ? .semibold : .regular))
                        .foregroundStyle(isSelected ? Color.white : Color.primary)
                        .padding(.vertical, 10)
                        .padding(.horizontal, 12)
                        .frame(maxWidth: .infinity)
                        .contentShape(.capsule)
                    }
                    .buttonStyle(.plain)
                    .background {
                        if isSelected {
                            Capsule()
                                .fill(.clear)
                                .glassEffect(.regular.tint(.accentColor).interactive(), in: .capsule)
                                .glassEffectID("selection", in: namespace)
                        }
                    }
                    .accessibilityAddTraits(isSelected ? .isSelected : [])
                }
            }
            .padding(4)
            .background(Color.primary.opacity(0.06), in: .capsule)
        }
    }
}

/// Horizontally scrolling glass chips, for longer option lists such as resolutions.
struct GlassChipRow<Option: Hashable & Identifiable>: View {
    let options: [Option]
    @Binding var selection: Option
    let label: (Option) -> String

    var body: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            GlassEffectContainer(spacing: 10) {
                HStack(spacing: 8) {
                    ForEach(options) { option in
                        let isSelected = option == selection
                        Button {
                            withAnimation(.bouncy(duration: 0.35)) { selection = option }
                        } label: {
                            Text(label(option))
                                .font(.subheadline.weight(isSelected ? .semibold : .medium))
                                .monospacedDigit()
                                .foregroundStyle(isSelected ? Color.white : Color.primary)
                                .padding(.vertical, 9)
                                .padding(.horizontal, 16)
                        }
                        .buttonStyle(.plain)
                        .glassEffect(
                            isSelected ? .regular.tint(.accentColor).interactive() : .regular.interactive(),
                            in: .capsule
                        )
                    }
                }
                .padding(.horizontal, 2)
                .padding(.vertical, 4)
            }
        }
        .scrollClipDisabled()
    }
}

// MARK: - Progress

/// A capsule progress bar with a glass track; indeterminate when `value` is nil.
struct GlassProgressBar: View {
    let value: Double?

    @State private var phase: CGFloat = -0.4

    var body: some View {
        GeometryReader { proxy in
            let width = proxy.size.width
            ZStack(alignment: .leading) {
                Capsule().fill(Color.primary.opacity(0.08))
                if let value {
                    Capsule()
                        .fill(Color.accentColor)
                        .frame(width: max(8, width * min(max(value, 0), 1)))
                        .animation(.smooth(duration: 0.3), value: value)
                } else {
                    Capsule()
                        .fill(Color.accentColor.opacity(0.75))
                        .frame(width: width * 0.35)
                        .offset(x: width * phase)
                        .onAppear {
                            withAnimation(.easeInOut(duration: 1.1).repeatForever(autoreverses: true)) {
                                phase = 0.65
                            }
                        }
                }
            }
            .clipShape(.capsule)
        }
        .frame(height: 8)
        .glassEffect(.clear, in: .capsule)
        .accessibilityElement()
        .accessibilityLabel("İlerleme")
        .accessibilityValue(value.map { "%\(Int($0 * 100))" } ?? "Belirsiz")
    }
}

/// A circular progress ring, used in the compact tab bar accessory.
struct ProgressRing: View {
    let value: Double?
    var lineWidth: CGFloat = 3

    @State private var spin = false

    var body: some View {
        ZStack {
            Circle().stroke(Color.primary.opacity(0.12), lineWidth: lineWidth)
            Circle()
                .trim(from: 0, to: value.map { CGFloat(min(max($0, 0.02), 1)) } ?? 0.25)
                .stroke(Color.accentColor, style: StrokeStyle(lineWidth: lineWidth, lineCap: .round))
                .rotationEffect(.degrees(value == nil ? (spin ? 360 : 0) : -90))
                .animation(value == nil ? Animation.linear(duration: 1).repeatForever(autoreverses: false) : Animation.smooth, value: spin)
                .animation(.smooth(duration: 0.3), value: value)
        }
        .onAppear { spin = true }
    }
}

// MARK: - Thumbnails

struct MediaThumbnail: View {
    let urlString: String?
    let kind: MediaKind
    var cornerRadius: CGFloat = 16

    var body: some View {
        ZStack {
            Color(uiColor: .tertiarySystemFill)
            Image(systemName: kind.symbol)
                .font(.title2)
                .foregroundStyle(.secondary)
            if let urlString, let url = URL(string: urlString) {
                AsyncImage(url: url, transaction: Transaction(animation: .smooth)) { phase in
                    if let image = phase.image {
                        image
                            .resizable()
                            .scaledToFill()
                            .transition(.opacity)
                    }
                }
            }
        }
        .clipShape(.rect(cornerRadius: cornerRadius))
    }
}

// MARK: - Empty states

struct GlassEmptyState<Actions: View>: View {
    let symbol: String
    let title: String
    let message: String
    @ViewBuilder var actions: Actions

    var body: some View {
        VStack(spacing: 18) {
            Image(systemName: symbol)
                .font(.system(size: 44, weight: .medium))
                .foregroundStyle(Color.accentColor)
                .frame(width: 96, height: 96)
                .glassEffect(.regular.tint(.accentColor.opacity(0.15)), in: .circle)
            VStack(spacing: 6) {
                Text(title).font(.title3.bold())
                Text(message)
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
                    .multilineTextAlignment(.center)
            }
            actions
        }
        .padding(28)
        .frame(maxWidth: 420)
        .glassEffect(.regular, in: .rect(cornerRadius: 36))
        .padding(.horizontal, 20)
    }
}
