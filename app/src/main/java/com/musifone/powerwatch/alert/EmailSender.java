package com.musifone.powerwatch.alert;

import com.musifone.powerwatch.data.SettingsStore;

import java.util.Properties;

import javax.mail.Authenticator;
import javax.mail.Message;
import javax.mail.PasswordAuthentication;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;

public final class EmailSender {
    private EmailSender() {
    }

    public static void send(SettingsStore settings, String subject, String body) throws Exception {
        if (!settings.isEmailConfigured()) {
            throw new IllegalStateException("Email is not configured");
        }

        Properties props = new Properties();
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.starttls.enable", "true");
        props.put("mail.smtp.host", settings.getSmtpHost());
        props.put("mail.smtp.port", String.valueOf(settings.getSmtpPort()));
        props.put("mail.smtp.connectiontimeout", "15000");
        props.put("mail.smtp.timeout", "15000");
        props.put("mail.smtp.writetimeout", "15000");

        final String user = settings.getSmtpUser();
        final String pass = settings.getSmtpPass();

        Session session = Session.getInstance(props, new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(user, pass);
            }
        });

        String from = settings.getSmtpFrom();
        if (from == null || from.isEmpty()) {
            from = user;
        }

        MimeMessage message = new MimeMessage(session);
        message.setFrom(new InternetAddress(from));
        message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(settings.getEmailTo(), false));
        message.setSubject(subject, "UTF-8");
        message.setText(body, "UTF-8");
        Transport.send(message);
    }
}
