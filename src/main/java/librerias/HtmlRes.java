package librerias;

import java.util.List;

/**
 * Clase para generar HTML con estilos modernos para emails
 */
public class HtmlRes {

    private static final String CSS_STYLES =
        "<style>" +
        "body { font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif; margin: 0; padding: 0; background: #1f3a5f; }" +
        ".container { width: 100%; max-width: none; margin: 0; background: white; }" +
        ".header { background: #1f3a5f; color: white; padding: 30px 20px; text-align: center; }" +
        ".header h1 { margin: 0; font-size: 26px; font-weight: 300; }" +
        ".header h2 { margin: 12px 0 0 0; font-size: 22px; font-weight: 300; }" +
        ".content { padding: 30px 20px; }" +
        ".success-message { background: #2e7d32; color: white; padding: 25px; border-radius: 12px; margin: 20px 0; text-align: center; }" +
        ".error-message { background: #c62828; color: white; padding: 25px; border-radius: 12px; margin: 20px 0; text-align: center; }" +
        ".info-message { background: #1565c0; color: white; padding: 25px; border-radius: 12px; margin: 20px 0; text-align: center; }" +
        ".table-container { margin: 20px 0; overflow-x: auto; }" +
        ".modern-table { width: 100%; border-collapse: collapse; }" +
        ".modern-table th { background: #1f3a5f; color: white; padding: 12px; text-align: left; font-size: 14px; }" +
        ".modern-table td { padding: 12px; border-bottom: 1px solid #ddd; font-size: 14px; }" +
        ".modern-table tr:nth-child(even) { background-color: #f4f6f8; }" +
        ".footer { background: #f4f6f8; padding: 20px; text-align: center; color: #555; font-size: 13px; }" +
        "</style>";

    public static String generateTable(String title, String[] headers, List<String[]> data) {
        StringBuilder tableHtml = new StringBuilder();

        tableHtml.append("<div class='table-container'>");
        tableHtml.append("<table class='modern-table'>");
        tableHtml.append("<thead><tr>");
        for (String header : headers) {
            tableHtml.append("<th>").append(escape(header)).append("</th>");
        }
        tableHtml.append("</tr></thead>");

        tableHtml.append("<tbody>");
        for (String[] row : data) {
            tableHtml.append("<tr>");
            for (String cell : row) {
                tableHtml.append("<td>").append(cell != null ? escape(cell) : "").append("</td>");
            }
            tableHtml.append("</tr>");
        }
        tableHtml.append("</tbody>");
        tableHtml.append("</table>");
        tableHtml.append("</div>");

        return generateFullHtml(title, tableHtml.toString());
    }

    public static String generateSuccessMessage(String title, String message) {
        return generateMessage("success-message", title, message);
    }

    public static String generateErrorMessage(String title, String message) {
        return generateMessage("error-message", title, message);
    }

    public static String generateInfoMessage(String title, String message) {
        return generateMessage("info-message", title, message);
    }

    /** Datos del cobro y el QR, que va en el correo como imagen con Content-ID "cid". */
    public static String generateQrMessage(String title, String[][] datos, String cid) {
        StringBuilder content = new StringBuilder("<div class='table-container'><table class='modern-table'><tbody>");
        for (String[] dato : datos) {
            content.append("<tr><td><strong>").append(escape(dato[0])).append("</strong></td><td>")
                    .append(escape(dato[1])).append("</td></tr>");
        }
        content.append("</tbody></table></div>")
                .append("<p style='text-align:center'>Escanea el código QR con tu aplicación bancaria:</p>")
                .append("<p style='text-align:center'><img src='cid:").append(cid)
                .append("' alt='Código QR' style='max-width:300px'></p>");
        return generateFullHtml(title, content.toString());
    }

    private static String generateMessage(String cssClass, String title, String message) {
        return generateFullHtml(title, "<div class='" + cssClass + "'>"
                + "<h2>" + escape(title) + "</h2>"
                + "<p>" + escape(message) + "</p>"
                + "</div>");
    }

    // Los datos los escribe el usuario y se devuelven dentro de un correo HTML.
    public static String escape(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }

    private static String generateFullHtml(String title, String content) {
        return "<!DOCTYPE html>" +
               "<html lang='es'>" +
               "<head>" +
               "<meta charset='UTF-8'>" +
               "<meta name='viewport' content='width=device-width, initial-scale=1.0'>" +
               "<title>" + escape(title) + "</title>" +
               CSS_STYLES +
               "</head>" +
               "<body>" +
               "<div class='container'>" +
               "<div class='header'>" +
               "<h1>Sistema de Gestión Académica ICAP-UAGRM</h1>" +
               "<h2>" + escape(title) + "</h2>" +
               "</div>" +
               "<div class='content'>" +
               content +
               "</div>" +
               "<div class='footer'>" +
               "<p>Envía HELP en el asunto para ver tus comandos | " +
               java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss")) + "</p>" +
               "</div>" +
               "</div>" +
               "</body>" +
               "</html>";
    }
}
