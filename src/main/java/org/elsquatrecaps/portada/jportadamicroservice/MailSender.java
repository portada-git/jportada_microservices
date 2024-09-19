package org.elsquatrecaps.portada.jportadamicroservice;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.Properties; 
import javax.mail.Authenticator;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.PasswordAuthentication;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.AddressException;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;

/**
 *
 * @author josep
 */
public class MailSender {
    private static final Properties properties = new Properties();
    private String username;
    private String password;
    private String admin;

    public MailSender() {
        
    }
    
    public MailSender(String username, String password) {
        this.username = username;
        this.password = password;
        properties.put("mail.smtp.host", "smtp.gmail.com");
        properties.put("mail.smtp.port", "465");
        properties.put("mail.smtp.auth", "true");
        properties.put("mail.smtp.socketFactory.port", "465");
        properties.put("mail.smtp.socketFactory.class", "javax.net.ssl.SSLSocketFactory");
        admin = username;        
    }

    public MailSender(String jsonContent) {
        JsonObject json = JsonParser.parseString(jsonContent).getAsJsonObject();
        this.username = json.get("username").getAsString();
        this.password = json.get("password").getAsString();
        properties.put("mail.smtp.host", json.get("mail.smtp.host").getAsString());
        properties.put("mail.smtp.port", json.get("mail.smtp.port").getAsString());
        properties.put("mail.smtp.auth", json.get("mail.smtp.auth").getAsString());
        properties.put("mail.smtp.socketFactory.port", json.get("mail.smtp.socketFactory.port").getAsString());
        properties.put("mail.smtp.socketFactory.class", json.get("mail.smtp.socketFactory.class").getAsString());
        admin = json.get("adminAdress").getAsString();
        if(admin.isEmpty()){
            admin = username;
        }
    }
    
    public void setCredentials(String username, String password) {
        this.username = username;
        this.password = password;
    }
    
    public void setCredentials(String jsonContent) {
        JsonObject json = JsonParser.parseString(jsonContent).getAsJsonObject();
        this.username = json.get("username").getAsString();
        this.password = json.get("password").getAsString();
        properties.put("mail.smtp.host", json.get("mail.smtp.host").getAsString());
        properties.put("mail.smtp.port", json.get("mail.smtp.port").getAsString());
        properties.put("mail.smtp.auth", json.get("mail.smtp.auth").getAsString());
        properties.put("mail.smtp.socketFactory.port", json.get("mail.smtp.socketFactory.port").getAsString());
        properties.put("mail.smtp.socketFactory.class", json.get("mail.smtp.socketFactory.class").getAsString());
    }
    
    public void sendMessageToAdmin(String subject, String textMessage) throws AddressException, MessagingException{
        sendMessageToMailAdress(subject, textMessage, admin);
    }
    
    public void sendMessageToMailAdress(String subject, String textMessage, String mailAdress) throws AddressException, MessagingException{
        Session session = Session.getInstance(properties,
                new Authenticator() {
                    @Override
                    protected PasswordAuthentication getPasswordAuthentication() {
                        return new PasswordAuthentication(username, password);
                    }
                });
        Message message = new MimeMessage(session);
        message.setFrom(new InternetAddress(username));
        message.setRecipients(
                Message.RecipientType.TO,
                InternetAddress.parse(mailAdress)
        );
        message.setSubject(subject);
        message.setText(String.format("%s\n\n Please do not spam this email!", textMessage));

        Transport.send(message);
    }    
}
