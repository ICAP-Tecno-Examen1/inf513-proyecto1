package postgresConecction;

import io.github.cdimascio.dotenv.Dotenv;

public class DBConnection {

    /*
     * true  -> base local (localhost / prueba15sa). Usuario y clave en .env (DB_USER, DB_PASSWORD).
     * false -> base del ing (www.tecnoweb.org.bo / db_grupo15sa), los valores de abajo.
     */
    public static final boolean USE_LOCAL = true;

    // Servidor del ing (no borrar: se usa el día del examen)
    private static final String ING_SERVER = "www.tecnoweb.org.bo";
    private static final String ING_DATABASE = "db_grupo15sa";
    private static final String ING_USER = "grupo15sa";
    private static final String ING_PASSWORD = "grup015grup015*";

    // Base local
    private static final String LOCAL_SERVER = "localhost";
    private static final String LOCAL_DATABASE = "prueba15sa";

    public static String database = USE_LOCAL ? LOCAL_DATABASE : ING_DATABASE;
    public static String server = USE_LOCAL ? LOCAL_SERVER : ING_SERVER;
    public static String port = "5432";
    public static String user = USE_LOCAL ? variableEntorno("DB_USER", "postgres") : ING_USER;
    public static String password = USE_LOCAL ? variableEntorno("DB_PASSWORD", "") : ING_PASSWORD;
    public static String url = "jdbc:postgresql://" + server + ":" + port + "/" + database;

    private static String variableEntorno(String nombre, String porDefecto) {
        try {
            String valor = Dotenv.load().get(nombre);
            return valor != null ? valor : porDefecto;
        } catch (Exception e) {
            return porDefecto;
        }
    }
}
