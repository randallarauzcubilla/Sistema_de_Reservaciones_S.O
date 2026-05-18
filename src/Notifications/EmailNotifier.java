package Notifications;

import Core.AppPaths;
import java.io.File;
import java.io.FileReader;
import java.util.Properties;
import javax.mail.Session;

/**
 * Utility class responsible for sending email notifications using SMTP.
 * Configured to use Office365 SMTP server with TLS authentication.
 */
public class EmailNotifier {

    /**
     * Base directory where application data files are stored.
     */
    private static final String BASE_DIR = AppPaths.getDataDir();

    /**
     * Full path to the email configuration file.
     */
    private static final String CONFIG_FILE
            = BASE_DIR + File.separator + "email.properties";

    /**
     * Sends an email message to the specified recipient using SMTP.
     *
     * @param toEmail recipient email address
     * @param subject subject of the email
     * @param body content of the email message
     */
    public static void send(String toEmail, String subject, String body) {
        File configFile = new File(CONFIG_FILE);
        System.out.println("[EMAIL] Buscando config en: "
                + configFile.getAbsolutePath());
        System.out.println("[EMAIL] Existe: " + configFile.exists());
        if (!configFile.exists()) {
            return;
        }
        System.out.println("[EMAIL] Cargando config desde: "
                + configFile.getAbsolutePath());

        try {
            Properties config = new Properties();
            config.load(new FileReader(configFile));

            String from = config.getProperty("mail.from").trim();
            String password = config.getProperty("mail.password").trim();
            String host = config.getProperty("mail.smtp.host").trim();
            String port = config.getProperty("mail.smtp.port").trim();

            System.out.println("[EMAIL] From: " + from);
            System.out.println("[EMAIL] Host: " + host + ":" + port);

            Properties props = new Properties();
            props.put("mail.smtp.host", host);
            props.put("mail.smtp.port", port);
            props.put("mail.smtp.auth", "true");
            props.put("mail.smtp.ssl.enable", "true");
            props.put("mail.smtp.starttls.enable", "false");
            props.put("mail.smtp.socketFactory.port", "465");
            props.put("mail.smtp.socketFactory.class",
                    "javax.net.ssl.SSLSocketFactory");
            props.put("mail.smtp.connectiontimeout", "8000");
            props.put("mail.smtp.timeout", "8000");
            props.put("mail.smtp.writetimeout", "8000");

            Session session = Session.getInstance(props,
                    new javax.mail.Authenticator() {
                @Override
                protected javax.mail.PasswordAuthentication
                        getPasswordAuthentication() {
                    return new javax.mail.PasswordAuthentication(
                            from, password);
                }
            });

            javax.mail.Message msg
                    = new javax.mail.internet.MimeMessage(session);
            msg.setFrom(
                    new javax.mail.internet.InternetAddress(from));
            msg.setRecipients(
                    javax.mail.Message.RecipientType.TO,
                    javax.mail.internet.InternetAddress.parse(toEmail));
            msg.setSubject(subject);
            msg.setText(body);
            javax.mail.Transport.send(msg);
            System.out.println("[EMAIL] Enviado a: " + toEmail);

        } catch (Exception e) {
            System.out.println("[EMAIL] Error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
