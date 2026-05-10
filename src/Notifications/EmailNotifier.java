package Notifications;

import java.util.Properties;
import javax.mail.MessagingException;
import javax.mail.Session;

/**
 * Utility class responsible for sending email notifications using SMTP.
 * Configured to use Office365 SMTP server with TLS authentication.
 */
public class EmailNotifier {

    /**
     * Sends an email message to the specified recipient using SMTP.
     *
     * @param toEmail recipient email address
     * @param subject subject of the email
     * @param body content of the email message
     */
    public static void send(String toEmail, String subject, String body) {
        Properties props = new Properties();
        props.put("mail.smtp.host", "smtp.office365.com");
        props.put("mail.smtp.port", "587");
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.starttls.enable", "true");

        Session session = Session.getInstance(props,
                new javax.mail.Authenticator() {
            @Override
            protected javax.mail.PasswordAuthentication
                    getPasswordAuthentication() {
                return new javax.mail.PasswordAuthentication(
                        "servidor@una.ac.cr", "PASSWORD");
            }
        });
        try {
            javax.mail.Message msg
                    = new javax.mail.internet.MimeMessage(session);
            msg.setFrom(new javax.mail.internet.InternetAddress(
                    "servidor@una.ac.cr"));
            msg.setRecipients(javax.mail.Message.RecipientType.TO,
                    javax.mail.internet.InternetAddress.parse(toEmail));
            msg.setSubject(subject);
            msg.setText(body);
            javax.mail.Transport.send(msg);
        } catch (MessagingException e) {
            System.out.println("[EMAIL] Error: " + e.getMessage());
        }
    }
}