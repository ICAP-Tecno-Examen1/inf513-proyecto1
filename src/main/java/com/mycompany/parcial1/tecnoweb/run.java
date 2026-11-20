package com.mycompany.parcial1.tecnoweb;

import postgresConecction.WebHookServer;
import io.github.cdimascio.dotenv.Dotenv;

public class run {

    public static final boolean USE_GMAIL = true; // false: responde desde grupo15sa@tecnoweb.org.bo (SMTP puerto 25)

    public static String GMAIL_USER = "jazz.tb.99@gmail.com";
    public static String GMAIL_APP_PASSWORD = "qdztkthuvmxhzguo";

    public static final String GMAIL_PORT = "465";


    public static void main(String[] args) {
        try {
            Dotenv dotenv = Dotenv.load();
            if (dotenv.get("GMAIL_USER") != null) GMAIL_USER = dotenv.get("GMAIL_USER");
            if (dotenv.get("GMAIL_APP_PASSWORD") != null) GMAIL_APP_PASSWORD = dotenv.get("GMAIL_APP_PASSWORD");
            System.out.println("Variables de entorno cargadas desde .env");
        } catch (Exception e) {
            System.out.println("No se encontro .env, usando valores por defecto");
        }

        System.out.println("===================================");
        System.out.println("Iniciando EmailApp");
        if (USE_GMAIL) {
            System.out.println("Modo: GMAIL SMTP");
            System.out.println("Usuario: " + GMAIL_USER);
            System.out.println("Puerto: " + GMAIL_PORT + " (SSL)");
        } else {
            System.out.println("Modo: SERVIDOR TECNOWEB");
            System.out.println("Servidor: www.tecnoweb.org.bo");
        }
        System.out.println("===================================");

        WebHookServer webhookServer = new WebHookServer();
        webhookServer.start();

        EmailApp app = new EmailApp();
        app.start();
    }
}
