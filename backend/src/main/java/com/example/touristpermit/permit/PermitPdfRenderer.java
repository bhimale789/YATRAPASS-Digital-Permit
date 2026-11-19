package com.example.touristpermit.permit;

import com.example.touristpermit.area.PermitArea;
import com.example.touristpermit.config.ClockConfig;
import com.example.touristpermit.tourist.Gender;
import com.example.touristpermit.tourist.TouristProfile;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.util.Matrix;
import org.springframework.stereotype.Component;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.text.Normalizer;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Renders a printable A4 permit with PDFBox: holder, area, validity, purpose, conditions and,
 * while the permit can still verify, its QR code.
 * <p>
 * A permit that cannot be used for travel (cancelled, expired, completed) is still printable as a
 * record, but carries a large watermark and no QR code. The standard Helvetica font only covers
 * Windows-1252, so other characters lose their accents or print as '?'.
 */
@Component
public class PermitPdfRenderer {

    private static final PDRectangle PAGE = PDRectangle.A4;
    private static final float MARGIN = 48;
    private static final float CONTENT_WIDTH = PAGE.getWidth() - 2 * MARGIN;
    private static final float HEADER_HEIGHT = 96;
    private static final float FOOTER_HEIGHT = 36;
    private static final float QR_SIZE = 150;
    private static final float LABEL_WIDTH = 130;
    private static final float BODY = 10;
    private static final float LEADING = 13;
    private static final float ROW_GAP = 4;

    private static final Color BRAND = new Color(0x1F, 0x3A, 0x5F);
    private static final Color BRAND_LIGHT = new Color(0xC9, 0xD6, 0xE8);
    private static final Color INK = new Color(0x1F, 0x29, 0x37);
    private static final Color MUTED = new Color(0x6B, 0x72, 0x80);
    private static final Color RULE = new Color(0xD1, 0xD5, 0xDB);
    private static final Color OK = new Color(0x15, 0x80, 0x3D);
    private static final Color OK_BG = new Color(0xDC, 0xFC, 0xE7);
    private static final Color BAD = new Color(0xB9, 0x1C, 0x1C);
    private static final Color BAD_BG = new Color(0xFE, 0xE2, 0xE2);
    private static final Color WATERMARK = new Color(0xFC, 0xE8, 0xE8);

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH);
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter
            .ofPattern("d MMM yyyy, HH:mm 'NPT'", Locale.ENGLISH).withZone(ClockConfig.PERMIT_ZONE);

    private static final List<String> CONDITIONS = List.of(
            "Carry this permit, printed or on your phone, together with the passport listed above at all times "
                    + "while in the permitted area.",
            "Present the QR code at every checkpoint. Officers verify it online; the result on the officer's "
                    + "device is authoritative, not this printout.",
            "The permit is valid only for the area and dates stated above and is not transferable.",
            "Treat the QR code like a password and do not post or share it. If it is lost or exposed, ask the "
                    + "permit office for a new one; the old code then stops working.");

    private final Clock clock;

    public PermitPdfRenderer(Clock clock) {
        this.clock = clock;
    }

    /**
     * @param qrPng the permit's QR image, or null when the permit must not carry a usable QR code
     */
    public byte[] render(Permit permit, byte[] qrPng) {
        Instant now = clock.instant();
        Standing standing = Standing.of(permit, LocalDate.now(clock));
        try (PDDocument doc = new PDDocument()) {
            describe(doc, permit, now);
            Canvas canvas = new Canvas(doc, standing);
            canvas.header(permit);
            canvas.statusBox(standing);
            canvas.permitSection(permit, qrPng, standing);
            canvas.holderSection(permit.getTourist());
            canvas.areaSection(permit.getArea());
            canvas.paragraphSection("Purpose of visit", permit.getApplication().getPurpose());
            canvas.bulletSection("Conditions", CONDITIONS);
            canvas.close();
            canvas.footers("Generated " + DATE_TIME.format(now) + "  ·  Permit " + permit.getPermitNumber());

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.save(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("Permit PDF rendering failed", e);
        }
    }

    private static void describe(PDDocument doc, Permit permit, Instant now) {
        PDDocumentInformation info = doc.getDocumentInformation();
        info.setTitle("Tourist area permit " + permit.getPermitNumber());
        info.setCreator("YatraPass - Digital Tourist Permit & QR Verification System");
        GregorianCalendar created = new GregorianCalendar(TimeZone.getTimeZone(ClockConfig.PERMIT_ZONE));
        created.setTimeInMillis(now.toEpochMilli());
        info.setCreationDate(created);
    }

    /** How the printout presents the permit today. */
    record Standing(String label, boolean usable, String note) {

        static Standing of(Permit p, LocalDate today) {
            return switch (p.getStatus()) {
                case ACTIVE -> {
                    if (p.isPastValidity(today)) {
                        yield expired(p);
                    }
                    String window = "from " + DATE.format(p.getValidFrom()) + " to " + DATE.format(p.getValidUntil());
                    yield today.isBefore(p.getValidFrom())
                            ? new Standing("ACTIVE", true, "Not yet in effect. Valid for travel " + window + ".")
                            : new Standing("VALID", true, "Valid for travel " + window + ".");
                }
                case EXPIRED -> expired(p);
                case CANCELLED -> new Standing("CANCELLED", false, "This permit has been cancelled and is not valid "
                        + "for travel." + (p.getCancellationReason() == null ? "" : " Reason: "
                        + p.getCancellationReason()));
                case COMPLETED -> new Standing("COMPLETED", false,
                        "This permit has been completed and closed. It is not valid for further travel.");
            };
        }

        private static Standing expired(Permit p) {
            return new Standing("EXPIRED", false, "This permit expired after " + DATE.format(p.getValidUntil())
                    + " and is not valid for travel.");
        }
    }

    /** Page cursor over the document: draws top-down and starts a new page when content would overflow. */
    private static final class Canvas {

        private final PDDocument doc;
        private final Standing standing;
        private final PDFont regular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
        private final PDFont bold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
        private PDPageContentStream cs;
        private float y;

        Canvas(PDDocument doc, Standing standing) throws IOException {
            this.doc = doc;
            this.standing = standing;
            newPage();
        }

        // ------------------------------------------------------------ sections

        void header(Permit permit) throws IOException {
            float top = PAGE.getHeight();
            fillRect(0, top - HEADER_HEIGHT, PAGE.getWidth(), HEADER_HEIGHT, BRAND);
            text("TOURIST AREA PERMIT", MARGIN, top - 46, bold, 20, Color.WHITE);
            text("YatraPass - Digital Tourist Permit & QR Verification System", MARGIN, top - 66, regular, 10, BRAND_LIGHT);
            float right = MARGIN + CONTENT_WIDTH;
            textRight("PERMIT NO.", right, top - 42, bold, 8, BRAND_LIGHT);
            textRight(permit.getPermitNumber(), right, top - 62, bold, 15, Color.WHITE);
            y = top - HEADER_HEIGHT - 24;
        }

        void statusBox(Standing s) throws IOException {
            List<String> note = wrap(s.note(), regular, 9, CONTENT_WIDTH - 24);
            float height = 30 + note.size() * 12;
            fillRect(MARGIN, y - height, CONTENT_WIDTH, height, s.usable() ? OK_BG : BAD_BG);
            fillRect(MARGIN, y - height, 4, height, s.usable() ? OK : BAD);
            text("STATUS: " + s.label(), MARGIN + 14, y - 18, bold, 12, s.usable() ? OK : BAD);
            for (int i = 0; i < note.size(); i++) {
                text(note.get(i), MARGIN + 14, y - 32 - i * 12, regular, 9, INK);
            }
            y -= height + 8;
        }

        void permitSection(Permit p, byte[] qrPng, Standing s) throws IOException {
            heading("Permit");
            float top = y + LEADING - 2;
            float rowsWidth = CONTENT_WIDTH - QR_SIZE - 20;
            long days = ChronoUnit.DAYS.between(p.getValidFrom(), p.getValidUntil()) + 1;
            row("Permit number", p.getPermitNumber(), rowsWidth, bold);
            row("Application number", p.getApplication().getApplicationNumber(), rowsWidth, regular);
            row("Status", p.getStatus().name(), rowsWidth, regular);
            row("Issued on", DATE.format(p.getIssueDate()), rowsWidth, regular);
            row("Valid from", DATE.format(p.getValidFrom()), rowsWidth, bold);
            row("Valid until", DATE.format(p.getValidUntil()), rowsWidth, bold);
            row("Duration", days + (days == 1 ? " day" : " days"), rowsWidth, regular);
            float rowsBottom = y;

            float qrX = MARGIN + CONTENT_WIDTH - QR_SIZE;
            float qrY = top - QR_SIZE;
            strokeRect(qrX, qrY, QR_SIZE, QR_SIZE, RULE);
            if (qrPng != null) {
                PDImageXObject image = PDImageXObject.createFromByteArray(doc, qrPng, "permit-qr.png");
                cs.drawImage(image, qrX + 1, qrY + 1, QR_SIZE - 2, QR_SIZE - 2);
                textCentered("Scan at checkpoint to verify", qrX + QR_SIZE / 2, qrY - 12, bold, 8, INK);
            } else {
                String reason = s.usable() ? "QR code not available" : "No QR code: permit is " + s.label();
                String hint = s.usable() ? "Contact the permit office" : "Not valid for travel";
                textCentered(reason, qrX + QR_SIZE / 2, qrY + QR_SIZE / 2 + 4, bold, 8, s.usable() ? MUTED : BAD);
                textCentered(hint, qrX + QR_SIZE / 2, qrY + QR_SIZE / 2 - 9, regular, 8, MUTED);
            }
            y = Math.min(rowsBottom, qrY - 22);
        }

        void holderSection(TouristProfile t) throws IOException {
            heading("Permit holder");
            row("Full name", t.getFullName(), CONTENT_WIDTH, bold);
            row("Nationality", t.getNationality(), CONTENT_WIDTH, regular);
            row("Passport number", t.getPassportNumber(), CONTENT_WIDTH, bold);
            row("Date of birth", t.getDateOfBirth() == null ? null : DATE.format(t.getDateOfBirth()), CONTENT_WIDTH,
                    regular);
            row("Gender", gender(t.getGender()), CONTENT_WIDTH, regular);
            row("Phone", t.getPhone(), CONTENT_WIDTH, regular);
            if (hasText(t.getAddress())) {
                row("Address", t.getAddress(), CONTENT_WIDTH, regular);
            }
            row("Emergency contact", t.getEmergencyContact(), CONTENT_WIDTH, regular);
        }

        void areaSection(PermitArea a) throws IOException {
            heading("Permitted area");
            row("Area", a.getName(), CONTENT_WIDTH, bold);
            row("Area code", a.getCode(), CONTENT_WIDTH, regular);
            row("District", a.getDistrict(), CONTENT_WIDTH, regular);
            row("Province", a.getProvince(), CONTENT_WIDTH, regular);
        }

        void paragraphSection(String title, String body) throws IOException {
            heading(title);
            for (String line : wrap(body, regular, BODY, CONTENT_WIDTH)) {
                ensure(LEADING);
                text(line, MARGIN, y, regular, BODY, INK);
                y -= LEADING;
            }
        }

        void bulletSection(String title, List<String> items) throws IOException {
            heading(title);
            for (String item : items) {
                List<String> lines = wrap(item, regular, 9, CONTENT_WIDTH - 12);
                ensure(lines.size() * 12 + ROW_GAP);
                text("•", MARGIN, y, regular, 9, MUTED);
                for (String line : lines) {
                    text(line, MARGIN + 12, y, regular, 9, INK);
                    y -= 12;
                }
                y -= ROW_GAP;
            }
        }

        // ------------------------------------------------------------ building blocks

        private void heading(String title) throws IOException {
            ensure(28 + LEADING * 2);
            y -= 10;
            text(title.toUpperCase(Locale.ENGLISH), MARGIN, y, bold, 9, BRAND);
            y -= 5;
            cs.setStrokingColor(RULE);
            cs.setLineWidth(0.75f);
            cs.moveTo(MARGIN, y);
            cs.lineTo(MARGIN + CONTENT_WIDTH, y);
            cs.stroke();
            y -= LEADING + 2;
        }

        /** Label/value pair; long values wrap within the value column. Missing values print as a dash. */
        private void row(String label, String value, float width, PDFont valueFont) throws IOException {
            List<String> lines = wrap(hasText(value) ? value : "—", valueFont, BODY, width - LABEL_WIDTH);
            ensure(lines.size() * LEADING);
            text(label, MARGIN, y, regular, BODY, MUTED);
            for (int i = 0; i < lines.size(); i++) {
                text(lines.get(i), MARGIN + LABEL_WIDTH, y - i * LEADING, valueFont, BODY, INK);
            }
            y -= lines.size() * LEADING + ROW_GAP;
        }

        /** Starts a new page when the next block would run into the footer. */
        private void ensure(float height) throws IOException {
            if (y - height < MARGIN + FOOTER_HEIGHT) {
                cs.close();
                newPage();
                y = PAGE.getHeight() - MARGIN;
            }
        }

        private void newPage() throws IOException {
            PDPage page = new PDPage(PAGE);
            doc.addPage(page);
            cs = new PDPageContentStream(doc, page);
            if (!standing.usable()) {
                watermark(standing.label());
            }
        }

        /** Large diagonal status text behind the content, so an unusable permit is obvious on paper. */
        private void watermark(String label) throws IOException {
            float size = 84;
            float width = width(label, bold, size);
            cs.beginText();
            cs.setFont(bold, size);
            cs.setNonStrokingColor(WATERMARK);
            cs.setTextMatrix(Matrix.getRotateInstance(Math.toRadians(35), PAGE.getWidth() / 2,
                    PAGE.getHeight() / 2 - 80));
            cs.newLineAtOffset(-width / 2, 0);
            cs.showText(label);
            cs.endText();
        }

        void close() throws IOException {
            cs.close();
        }

        /** Footer on every page, drawn once the page count is known. */
        void footers(String left) throws IOException {
            int total = doc.getNumberOfPages();
            for (int i = 0; i < total; i++) {
                try (PDPageContentStream footer = new PDPageContentStream(doc, doc.getPage(i),
                        PDPageContentStream.AppendMode.APPEND, true, true)) {
                    cs = footer;
                    float lineY = MARGIN + FOOTER_HEIGHT - 14;
                    cs.setStrokingColor(RULE);
                    cs.setLineWidth(0.75f);
                    cs.moveTo(MARGIN, lineY);
                    cs.lineTo(MARGIN + CONTENT_WIDTH, lineY);
                    cs.stroke();
                    text(left, MARGIN, lineY - 14, regular, 8, MUTED);
                    textRight("Page " + (i + 1) + " of " + total, MARGIN + CONTENT_WIDTH, lineY - 14, regular, 8,
                            MUTED);
                }
            }
        }

        // ------------------------------------------------------------ primitives

        private void text(String s, float x, float baseline, PDFont font, float size, Color color) throws IOException {
            cs.beginText();
            cs.setFont(font, size);
            cs.setNonStrokingColor(color);
            cs.newLineAtOffset(x, baseline);
            cs.showText(printable(s));
            cs.endText();
        }

        private void textRight(String s, float right, float baseline, PDFont font, float size, Color color)
                throws IOException {
            text(s, right - width(s, font, size), baseline, font, size, color);
        }

        private void textCentered(String s, float centre, float baseline, PDFont font, float size, Color color)
                throws IOException {
            text(s, centre - width(s, font, size) / 2, baseline, font, size, color);
        }

        private void fillRect(float x, float y0, float w, float h, Color color) throws IOException {
            cs.setNonStrokingColor(color);
            cs.addRect(x, y0, w, h);
            cs.fill();
        }

        private void strokeRect(float x, float y0, float w, float h, Color color) throws IOException {
            cs.setStrokingColor(color);
            cs.setLineWidth(0.75f);
            cs.addRect(x, y0, w, h);
            cs.stroke();
        }

        private float width(String s, PDFont font, float size) throws IOException {
            return font.getStringWidth(printable(s)) / 1000 * size;
        }

        /** Greedy word wrap; a single word longer than the line is broken across lines. */
        private List<String> wrap(String s, PDFont font, float size, float maxWidth) throws IOException {
            List<String> lines = new ArrayList<>();
            String line = "";
            for (String word : printable(s).trim().split("\\s+")) {
                String candidate = line.isEmpty() ? word : line + " " + word;
                if (width(candidate, font, size) <= maxWidth) {
                    line = candidate;
                    continue;
                }
                if (!line.isEmpty()) {
                    lines.add(line);
                }
                while (word.length() > 1 && width(word, font, size) > maxWidth) {
                    int cut = word.length() - 1;
                    while (cut > 1 && width(word.substring(0, cut), font, size) > maxWidth) {
                        cut--;
                    }
                    lines.add(word.substring(0, cut));
                    word = word.substring(cut);
                }
                line = word;
            }
            if (!line.isEmpty() || lines.isEmpty()) {
                lines.add(line);
            }
            return lines;
        }

        /**
         * Maps text onto what the standard font can show: whitespace and control characters become
         * spaces, accented letters outside Windows-1252 lose their accent, anything else becomes '?'.
         */
        private String printable(String s) {
            if (s == null) {
                return "";
            }
            StringBuilder out = new StringBuilder(s.length());
            s.codePoints().forEach(cp -> {
                if (Character.isWhitespace(cp) || Character.isISOControl(cp)) {
                    out.append(' ');
                    return;
                }
                String ch = Character.toString(cp);
                if (encodable(ch)) {
                    out.append(ch);
                    return;
                }
                String base = Normalizer.normalize(ch, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
                out.append(!base.isEmpty() && encodable(base) ? base : "?");
            });
            return out.toString();
        }

        private boolean encodable(String s) {
            try {
                regular.encode(s);
                return true;
            } catch (IllegalArgumentException | IOException e) {
                return false;
            }
        }
    }

    private static String gender(Gender g) {
        if (g == null) {
            return null;
        }
        return switch (g) {
            case MALE -> "Male";
            case FEMALE -> "Female";
            case OTHER -> "Other";
            case UNDISCLOSED -> "Prefer not to say";
        };
    }

    private static boolean hasText(String s) {
        return s != null && !s.isBlank();
    }
}
