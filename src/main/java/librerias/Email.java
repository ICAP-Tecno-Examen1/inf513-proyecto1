package librerias;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Email {
    public static final String SUBJECT = "Respuesta del Sistema ICAP";
    private String from;
    private String to;
    private String subject;
    private String message;

    public Email(String to, String subject, String message) {
        this.to = to;
        this.subject = subject;
        this.message = message;
    }

    public Email(String from, String subject) {
        this.from = from;
        this.subject = subject;
    }

    public String getFrom() {
        return from;
    }

    public String getTo() {
        return to;
    }

    public String getSubject() {
        return subject;
    }

    public String getMessage() {
        return message;
    }

    public static Email getEmail(String plainText) {
        // Solo se analiza la cabecera, y los campos largos se unen en una línea.
        String normalized = plainText.replace("\r\n", "\n");
        int endHeaders = normalized.indexOf("\n\n");
        String headers = (endHeaders >= 0 ? normalized.substring(0, endHeaders) : normalized)
                .replaceAll("\n[ \t]+", " ");

        String returnPath = getHeader(headers, "Return-Path");
        String from = returnPath != null ? returnPath : getHeader(headers, "From");
        String subject = getHeader(headers, "Subject");
        return new Email(extractAddress(from), subject != null ? subject : "");
    }

    private static String getHeader(String headers, String name) {
        Matcher matcher = Pattern.compile("(?im)^" + name + ":\\s*(.*)$").matcher(headers);
        return matcher.find() ? matcher.group(1).trim() : null;
    }

    private static String extractAddress(String value) {
        if (value == null) {
            return "";
        }
        int begin = value.indexOf('<');
        int end = value.indexOf('>', begin + 1);
        return (begin >= 0 && end > begin ? value.substring(begin + 1, end) : value).trim();
    }

    @Override
    public String toString() {
        return "[From: " + from + ", To: " + to + "]";
    }
}
