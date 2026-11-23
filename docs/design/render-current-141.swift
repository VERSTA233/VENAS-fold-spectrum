// Static sketches of the Nov 23 layout before/after wide-fold optimization.
// Assumptions: 578.1 x 410 dp, fontScale=1, no system insets, PLAYING.
// Run from project root; add --wide-fold for the optimized branch.
import AppKit
import CoreText
import ImageIO
import UniformTypeIdentifiers

let root = URL(fileURLWithPath: FileManager.default.currentDirectoryPath)
let wideFold = CommandLine.arguments.contains("--wide-fold")
let output = root.appendingPathComponent(wideFold ? "docs/design/wide-layout-1.41.png" : "docs/design/current-layout-1.41.png")
let width: CGFloat = 578.1
let height: CGFloat = 410
let scale: CGFloat = 1000 / height
let pixelWidth = 1410
let pixelHeight = 1000
let ctx = CGContext(data: nil, width: pixelWidth, height: pixelHeight,
    bitsPerComponent: 8, bytesPerRow: pixelWidth * 4,
    space: CGColorSpace(name: CGColorSpace.sRGB)!,
    bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue)!
ctx.translateBy(x: 0, y: CGFloat(pixelHeight))
ctx.scaleBy(x: scale, y: -scale)
ctx.textMatrix = CGAffineTransform(scaleX: 1, y: -1)
ctx.interpolationQuality = .high

func color(_ hex: Int) -> CGColor {
    CGColor(red: CGFloat((hex >> 16) & 255) / 255,
            green: CGFloat((hex >> 8) & 255) / 255,
            blue: CGFloat(hex & 255) / 255, alpha: 1)
}
let background = color(0x0E1012), ink = color(0xF3F4F5)
let muted = color(0x7E818A), grid = color(0x26292E), red = color(0xFF4D60)
func fill(_ rect: CGRect, _ color: CGColor) {
    ctx.setFillColor(color); ctx.fill(rect)
}
func line(_ x1: CGFloat, _ y1: CGFloat, _ x2: CGFloat, _ y2: CGFloat,
          _ color: CGColor, _ thickness: CGFloat = 1 / scale, dash: Bool = false) {
    ctx.saveGState()
    ctx.setStrokeColor(color); ctx.setLineWidth(thickness)
    if dash { ctx.setLineDash(phase: 0, lengths: [3 / scale, 6 / scale]) }
    ctx.move(to: CGPoint(x: x1, y: y1)); ctx.addLine(to: CGPoint(x: x2, y: y2))
    ctx.strokePath(); ctx.restoreGState()
}
func font(_ file: String, _ size: CGFloat) -> CTFont {
    let url = root.appendingPathComponent("app/src/main/res/font/" + file)
    let descriptors = CTFontManagerCreateFontDescriptorsFromURL(url as CFURL)! as! [CTFontDescriptor]
    var descriptor = descriptors[0]
    if file == "sf_pro.ttf" {
        descriptor = CTFontDescriptorCreateCopyWithAttributes(descriptor,
            [kCTFontVariationAttribute: [0x77676874: 400, 0x6F70737A: 17]] as CFDictionary)
    }
    return CTFontCreateWithFontDescriptor(descriptor, size, nil)
}
let monoName = "sf_mono_medium.otf"
func textLine(_ text: String, _ typeface: CTFont, _ color: CGColor, tracking: CGFloat = 0) -> CTLine {
    let attrs: [NSAttributedString.Key: Any] = [
        NSAttributedString.Key(kCTFontAttributeName as String): typeface,
        NSAttributedString.Key(kCTForegroundColorAttributeName as String): color,
        NSAttributedString.Key(kCTKernAttributeName as String): tracking,
    ]
    return CTLineCreateWithAttributedString(NSAttributedString(string: text, attributes: attrs))
}
func measure(_ text: String, _ typeface: CTFont, tracking: CGFloat = 0) -> CGFloat {
    CGFloat(CTLineGetTypographicBounds(textLine(text, typeface, ink, tracking: tracking), nil, nil, nil))
}
func text(_ string: String, _ x: CGFloat, _ baseline: CGFloat, _ typeface: CTFont,
          _ color: CGColor = ink, tracking: CGFloat = 0) {
    ctx.textPosition = CGPoint(x: x, y: baseline)
    CTLineDraw(textLine(string, typeface, color, tracking: tracking), ctx)
}
func centeredLine(_ string: String, _ x: CGFloat, _ top: CGFloat, _ lineHeight: CGFloat,
                  _ typeface: CTFont, _ color: CGColor = ink) {
    let natural = CTFontGetAscent(typeface) + CTFontGetDescent(typeface)
    text(string, x, top + (lineHeight - natural) / 2 + CTFontGetAscent(typeface), typeface, color)
}

fill(CGRect(x: 0, y: 0, width: width, height: height), background)
let u: CGFloat = wideFold ? min(1.35, max(0.5, min(height / 410, width / 580))) : min(1.35, max(0.65, height / 410))
let gap = width * (wideFold ? 0.04 : 0.08)
let cover = wideFold ? min(height * 0.52, width * 0.35) : min(height * 0.57, width * 0.27)
let coverTop = 33 * u
let rightX = 2 * gap + cover
let rightWidth = width - 3 * gap - cover

// Reuse the user's album artwork from their provided screenshot.
let sourceURL = URL(fileURLWithPath: "/Users/dxm/Downloads/Screenshot_2026-11-20-17-56-18-19_169627e03902025.jpg")
let source = CGImageSourceCreateWithURL(sourceURL as CFURL, nil)!
let album = CGImageSourceCreateImageAtIndex(source, 0, nil)!
    .cropping(to: CGRect(x: 209, y: 92, width: 650, height: 650))!
let coverRect = CGRect(x: gap, y: coverTop, width: cover, height: cover)
ctx.saveGState()
ctx.addPath(CGPath(roundedRect: coverRect, cornerWidth: 12, cornerHeight: 12, transform: nil))
ctx.clip()
ctx.translateBy(x: gap, y: coverTop + cover); ctx.scaleBy(x: 1, y: -1)
ctx.draw(album, in: CGRect(x: 0, y: 0, width: cover, height: cover))
ctx.restoreGState()
let titleTop = coverTop + cover + 16 * u
centeredLine("Destroy", gap + 8 * u, titleTop, 26 * u, font("sf_pro.ttf", 21 * u))
centeredLine("東京酒吐座", gap + 8 * u, titleTop + 31 * u, 20 * u,
             font("source_han_sans_sc_regular.otf", 15 * u), color(0xB9BBC0))

// Header: fixed-height row, then a weighted spectrum chart.
text("SPECTRUM", rightX, 42 * u, font("sf_mono_semibold.otf", 12 * u), tracking: 1.2 * u)
let statusFont = font(monoName, 10 * u)
let statusWidth = measure("PLAYING", statusFont, tracking: 0.4 * u)
let statusX = rightX + rightWidth - statusWidth
text("PLAYING", statusX, 41 * u, statusFont, tracking: 0.4 * u)
ctx.setFillColor(red)
ctx.fillEllipse(in: CGRect(x: statusX - 10 * u, y: 35.8 * u, width: 4 * u, height: 4 * u))

let chartTop = 52 * u
let chartHeight = height - 48 * u - 28 * u - 10 * u - 2 - 76 * u - 70 * u
let axisSize = 9 * u
let axisFont = font(monoName, axisSize)
let plotLeft = rightX + axisSize * 3.5
let plotRight = rightX + rightWidth - axisSize * 0.8
let plotTop = chartTop + axisSize * 2.3
let plotBottom = chartTop + chartHeight - axisSize * 0.5
for j in 0...4 {
    let y = plotTop + (plotBottom - plotTop) * CGFloat(j) / 4
    line(plotLeft, y, plotRight, y, grid, dash: true)
    let label = j == 0 ? "0" : "−\(j * 15)"
    text(label, plotLeft - axisSize * 0.8 - measure(label, axisFont), y + axisSize * 0.35, axisFont, muted)
}

// Same unique-bin grouping and axis interpolation as SpectrumMath (48kHz / 1024).
struct Band { let low: Double; let high: Double }
let binHz = 48000.0 / 1024
let last = Int(floor(20000 / binHz))
func slot(_ bin: Int) -> Int {
    min(63, Int(Float(log(Double(bin) * binHz / 20) / log(1000.0)) * 64))
}
var bands: [Band] = []
var start = 1
for bin in 1...last {
    if bin == last || slot(bin + 1) != slot(start) {
        bands.append(Band(low: start == 1 ? 20 : (Double(start) - 0.5) * binHz,
                          high: bin == last ? 20000 : (Double(bin) + 0.5) * binHz))
        start = bin + 1
    }
}
for (hz, label) in [(20.0,"20"),(200,"200"),(500,"500"),(1000,"1k"),(2000,"2k"),(4000,"4k"),(8000,"8k"),(16000,"16k")] {
    let index = bands.firstIndex(where: { hz <= $0.high })!
    let band = bands[index]
    let fraction = (Double(index) + (hz - band.low) / (band.high - band.low)) / Double(bands.count)
    let x = plotLeft + CGFloat(fraction) * (plotRight - plotLeft)
    line(x, plotTop, x, plotBottom, grid, dash: true)
    let tx = max(rightX, min(x - measure(label, axisFont) / 2, plotRight - measure(label, axisFont)))
    text(label, tx, chartTop + axisSize, axisFont, muted)
}
// Illustrative audio values only; they do not affect component geometry.
let values: [CGFloat] = [-24,-31,-34,-27,-39,-36,-34,-41,-38,-43,-36,-29,-20,-32,-35,-42,-39,-45,-46,-41,-43,-48,-46,-49,-45,-43,-42,-47,-51,-49,-47,-53,-54,-49,-51,-54,-52,-56,-55,-56,-58,-56,-59,-58]
let step = (plotRight - plotLeft) / CGFloat(bands.count)
for (i, db) in values.enumerated() {
    let h = (db + 60) / 60 * (plotBottom - plotTop)
    fill(CGRect(x: plotLeft + CGFloat(i) * step + step * 0.18,
                y: plotBottom - h, width: step * 0.62, height: h), color(0xB6B8BE))
}

let dividerY = chartTop + chartHeight + 10 * u
fill(CGRect(x: rightX, y: dividerY, width: rightWidth, height: 1), grid)
let progressTop = dividerY + 1
let progressHeight = 76 * u
let timeSize = 10 * u
let groupHeight = timeSize * (1.7 + 0.2 + 2.9)
let labelTop = progressTop + (progressHeight - groupHeight) / 2
let labelHeight = 1.7 * timeSize
let trackY = labelTop + labelHeight + timeSize * 0.2 + timeSize * 1.45
let edgeFont = font(monoName, timeSize)
centeredLine("0:00", rightX, labelTop, labelHeight, edgeFont, muted)
centeredLine("7:37", rightX + rightWidth - measure("7:37", edgeFont), labelTop, labelHeight, edgeFont, muted)
let fraction: CGFloat = 95 / 457
let playheadX = rightX + fraction * rightWidth
for i in 0..<81 {
    let x = rightX + rightWidth * CGFloat(i) / 80
    let h = i % 5 == 0 ? timeSize : timeSize * 0.5
    line(x, trackY - h, x, trackY + h, x <= playheadX ? color(0xB0B3BA) : color(0x393D44), 1.5 / scale)
}
line(playheadX, trackY - timeSize * 1.45, playheadX, trackY + timeSize * 1.45, red, 3 / scale)
let badgeFont = font(monoName, timeSize * 1.2)
let badgeWidth = measure("1:35", badgeFont) + timeSize * 1.3
let badgeLeft = max(rightX, min(playheadX - badgeWidth / 2, rightX + rightWidth - badgeWidth))
fill(CGRect(x: badgeLeft, y: labelTop, width: badgeWidth, height: labelHeight), ink)
centeredLine("1:35", badgeLeft + timeSize * 0.65, labelTop, labelHeight, badgeFont, color(0x000000))

let bottomDividerY = progressTop + progressHeight
fill(CGRect(x: rightX, y: bottomDividerY, width: rightWidth, height: 1), grid)
let metricsTop = bottomDividerY + 1 + 10 * u
let metricsHeight = 51 * u
let waveWeight: CGFloat = wideFold ? 1.25 : 2
let metricInset: CGFloat = (wideFold ? 6 : 14) * u
let quarter = (rightWidth - 1) / (waveWeight + 2)
let labelFont = font(monoName, 9 * u)
let levelX = rightX + waveWeight * quarter + 0.5
let dynamicX = levelX + quarter + 0.5
line(levelX - 0.5, metricsTop, levelX - 0.5, metricsTop + metricsHeight, grid, 0.5)
line(dynamicX - 0.5, metricsTop, dynamicX - 0.5, metricsTop + metricsHeight, grid, 0.5)
centeredLine("WAVEFORM", rightX, metricsTop, 12 * u, labelFont, muted)
let waveWidth = waveWeight * quarter - metricInset
let waveTop = metricsTop + 16 * u
let waveHeight = 35 * u
for i in 0..<48 {
    let t = CGFloat(i) / 47
    let amplitude = 0.08 + 0.5 * pow(sin(t * .pi * 3), 2) * (0.5 + 0.5 * cos(t * 5))
    let x = rightX + 1 + (waveWidth - 2) * t
    let h = amplitude * waveHeight * 0.42
    ctx.saveGState(); ctx.setLineCap(.round)
    line(x, waveTop + waveHeight / 2 - h, x, waveTop + waveHeight / 2 + h, ink,
         min(1.2, (waveWidth - 2) / 47 * 0.3))
    ctx.restoreGState()
}
func metric(_ label: String, _ value: String, _ unit: String, _ x: CGFloat, _ available: CGFloat) {
    ctx.saveGState()
    ctx.clip(to: CGRect(x: x, y: metricsTop, width: available, height: metricsHeight))
    centeredLine(label, x, metricsTop, 12 * u, labelFont, muted)
    let numberFont = font(monoName, (wideFold ? 18 : 20) * u)
    let unitFont = font(monoName, (wideFold ? 8 : 8.5) * u)
    let baseline = waveTop + (waveHeight - 24 * u) / 2 + 20 * u
    // Unweighted Row children consume remaining width in order. The number clips
    // first; the unit gets only the width left after the number and spacing.
    let numberWidth = measure(value, numberFont)
    text(value, x, baseline, numberFont)
    let unitX = x + min(available, numberWidth) + 3 * u
    if unitX < x + available { text(unit, unitX, baseline, unitFont, muted) }
    ctx.restoreGState()
}
metric("LEVEL", "−19.8", "dBFS", levelX + metricInset, quarter - 2 * metricInset)
metric("DYNAMIC", "17.0", "dB", dynamicX + metricInset, quarter - metricInset)

let image = ctx.makeImage()!
let destination = CGImageDestinationCreateWithURL(output as CFURL, UTType.png.identifier as CFString, 1, nil)!
CGImageDestinationAddImage(destination, image, nil)
precondition(CGImageDestinationFinalize(destination))
print("Saved \(output.path) (\(pixelWidth) × \(pixelHeight)); cover=\(cover)dp right=\(rightWidth)dp LEVEL=\(quarter - 2 * metricInset)dp")
