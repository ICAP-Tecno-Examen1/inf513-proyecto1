import static org.junit.jupiter.api.Assertions.*;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import librerias.Email;
import org.junit.jupiter.api.Test;
import postgresConecction.EmailReceipt;
import postgresConecction.EmailSend;

/**
 * Prueba el diálogo POP3 y SMTP contra servidores locales mínimos.
 */
class TransporteCorreoTest {

    @Test
    void recibeBorraYEntregaLosCorreos() throws Exception {
        List<String> ordenes = new ArrayList<>();
        BlockingQueue<List<Email>> recibidos = new ArrayBlockingQueue<>(1);
        try (ServerSocket servidor = new ServerSocket(0)) {
            Thread pop = new Thread(() -> atenderPop(servidor, ordenes));
            pop.setDaemon(true);
            pop.start();

            EmailReceipt.HOST = "localhost";
            EmailReceipt.PORT_POP = servidor.getLocalPort();
            EmailReceipt.USER = "usuario";
            EmailReceipt.PASSWORD = "clave";

            EmailReceipt receptor = new EmailReceipt();
            receptor.setEmailListener(recibidos::add);
            Thread hilo = new Thread(receptor);
            hilo.setDaemon(true);
            hilo.start();

            List<Email> emails = recibidos.poll(10, TimeUnit.SECONDS);
            hilo.interrupt();
            assertNotNull(emails, "No se recibieron correos");
            assertEquals(1, emails.size());
            assertEquals("ana@mail.com", emails.get(0).getFrom());
            assertEquals("LISOFE[]", emails.get(0).getSubject());
            pop.join(5000);
            assertEquals(List.of("USER usuario", "PASS clave", "STAT", "RETR 1", "DELE 1", "QUIT"), ordenes);
        }
    }

    private void atenderPop(ServerSocket servidor, List<String> ordenes) {
        try (Socket s = servidor.accept();
                BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream()));
                PrintWriter out = new PrintWriter(s.getOutputStream(), true)) {
            out.print("+OK listo\r\n");
            out.flush();
            String linea;
            while ((linea = in.readLine()) != null) {
                linea = linea.trim();
                ordenes.add(linea);
                if (linea.equals("STAT")) {
                    out.print("+OK 1 100\r\n");
                } else if (linea.startsWith("RETR")) {
                    out.print("+OK 100 octets\r\nReturn-Path: <ana@mail.com>\r\nSubject: LISOFE[]\r\n\r\ncuerpo\r\n.\r\n");
                } else {
                    out.print("+OK\r\n");
                }
                out.flush();
                if (linea.equals("QUIT")) {
                    return;
                }
            }
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void enviaLaRespuestaPorSmtp() throws Exception {
        StringBuilder mensaje = new StringBuilder();
        try (ServerSocket servidor = new ServerSocket(0)) {
            Thread smtp = new Thread(() -> atenderSmtp(servidor, mensaje));
            smtp.setDaemon(true);
            smtp.start();

            EmailSend.HOST = "localhost";
            EmailSend.PORT_SMTP = String.valueOf(servidor.getLocalPort());
            EmailSend.MAIL = "icap@mail.com";

            new EmailSend(new Email("ana@mail.com", "Matrícula confirmada", "<p>Hola</p>")).run();
            smtp.join(5000);
        }
        String texto = mensaje.toString();
        assertTrue(texto.contains("MAIL FROM:<icap@mail.com>"), texto);
        assertTrue(texto.contains("RCPT TO:<ana@mail.com>"), texto);
        assertTrue(texto.contains("text/html"), texto);
        assertTrue(texto.contains("<p>Hola</p>"), texto);
    }

    private void atenderSmtp(ServerSocket servidor, StringBuilder mensaje) {
        try (Socket s = servidor.accept();
                BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream()));
                PrintWriter out = new PrintWriter(s.getOutputStream(), true)) {
            out.print("220 listo\r\n");
            out.flush();
            boolean enDatos = false;
            String linea;
            while ((linea = in.readLine()) != null) {
                mensaje.append(linea).append('\n');
                if (enDatos) {
                    if (linea.equals(".")) {
                        enDatos = false;
                        out.print("250 ok\r\n");
                        out.flush();
                    }
                    continue;
                }
                if (linea.equals("DATA")) {
                    enDatos = true;
                    out.print("354 datos\r\n");
                } else if (linea.startsWith("QUIT")) {
                    out.print("221 fin\r\n");
                    out.flush();
                    return;
                } else {
                    out.print("250 ok\r\n");
                }
                out.flush();
            }
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
