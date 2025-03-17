package org.elsquatrecaps.portada.jportadamicroservice;

import java.io.File;
import java.io.FileReader;
import org.springframework.stereotype.Component;
import java.io.IOException;
import java.nio.file.Files;
import java.security.InvalidKeyException;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.SignatureException;
import java.security.spec.X509EncodedKeySpec;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.Properties;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@Component
public class SignatureVerificationFilter implements Filter {
    private static final SecureRandom secureRandom = new SecureRandom();  // Generador aleatori segur
    private final transient HashMap<String, ArrayList<PublicKey>> publicKeys = new HashMap<>();
    private static String defaultChallenge;

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        // Carrega totes les claus públiques
        try {
            Properties properties = new Properties();
            properties.load(new FileReader("/etc/.portada_microservices/papi_access.properties"));
            String publicKeyBasePath = properties.getProperty("publicKeyBasePath");
            String publicKeydirName = properties.getProperty("publicKeydirName");
            String[] teams = properties.getProperty("teams").split(",");
            defaultChallenge = properties.getProperty("papicli_access_signature_data");
            for(String team: teams){
                ArrayList<PublicKey> pkt = new ArrayList<>();
                publicKeys.put(team, pkt);
                File teamPath = new File(new File(publicKeyBasePath, team), publicKeydirName);
                if(teamPath.exists() && teamPath.isDirectory()){
                    for(File keyFile: teamPath.listFiles()){
                        pkt.add(loadPublicKey(keyFile.getAbsolutePath()));
                    }
                }
            }
        } catch (Exception e) {
            throw new ServletException("No s'han pogut carregar les claus públiques", e);
        }
    }
    
    private void reloatTeamKeys(String team) throws ServletException{
        // Carrega totes les claus públiques d'un equip determinat
        try {
            Properties properties = new Properties();
            properties.load(new FileReader("/etc/.portada_microservices/papi_access.properties"));
            String publicKeyBasePath = properties.getProperty("publicKeyBasePath");
            String publicKeydirName = properties.getProperty("publicKeydirName");
            defaultChallenge = properties.getProperty("papicli_access_signature_data");
            ArrayList<PublicKey> pkt = new ArrayList<>();
            publicKeys.put(team, pkt);
            File teamPath = new File(new File(publicKeyBasePath, team), publicKeydirName);
            if(teamPath.exists() && teamPath.isDirectory()){
                for(File keyFile: teamPath.listFiles()){
                    pkt.add(loadPublicKey(keyFile.getAbsolutePath()));
                }
            }
        } catch (Exception e) {
            throw new ServletException("No s'han pogut carregar les claus públiques de l'equip: ".concat(team), e);
        }
    }
    
    private boolean verifySignature(String signature, String challenge, String team){
        boolean verified=false ;

        for(int i=0; team!=null && !verified && i<publicKeys.get(team).size(); i++){
            try {
                Signature sig = Signature.getInstance("SHA256withRSA");
                sig.initVerify(publicKeys.get(team).get(i));
                sig.update(challenge.getBytes());
//                verified = sig.verify(signature.getBytes());
                verified = sig.verify(Base64.getDecoder().decode(signature));
            } catch (InvalidKeyException | NoSuchAlgorithmException | SignatureException e) {
                verified = false;
            }
        }
        return verified;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;
        String challenge;

        String signature = httpRequest.getHeader("X-Signature");
        challenge = (String) httpRequest.getSession().getAttribute("challenge");
        if(challenge==null){
            challenge = httpRequest.getHeader("X-Challenge");
             httpRequest.getSession().setAttribute("challenge", challenge);  
        }
        if(challenge==null){
            challenge=defaultChallenge;
            httpRequest.getSession().setAttribute("challenge", challenge);  
        }
        
        if(!httpRequest.getServletPath().equals("/") && !httpRequest.getServletPath().equals("/requestAccessPermission")
                && !httpRequest.getServletPath().equals("/verifyRequestedAcessPermission")
                && !httpRequest.getServletPath().equals("/test")){
            
            if (signature != null && challenge != null) {
                String team = httpRequest.getParameter("team");               
                if(!verifySignature(signature, challenge, team)) {
                    reloatTeamKeys(team);
                    if(!verifySignature(signature, challenge, team)) {                    
                        httpResponse.setStatus(HttpServletResponse.SC_FORBIDDEN);
                        return;
                    }
                }
            } else {
                httpResponse.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                //generar e challenge i enviar-lo
                byte[] challengeBytes = new byte[32];
                secureRandom.nextBytes(challengeBytes);
                challenge = Base64.getEncoder().encodeToString(challengeBytes);

                // Emmagatzemar el challenge a la sessió del servidor o a un altre sistema per verificar-ho després
                httpRequest.getSession().setAttribute("challenge", challenge);  
                httpResponse.addHeader("X-challenge", challenge);
                httpResponse.setContentType("application/json");
                httpResponse.getWriter().write("{\"challenge\":\"" + challenge + "\"}");   
                httpResponse.flushBuffer();
                return;
            }
        }

        chain.doFilter(request, response);  // Continuar amb la petició si la verificació és correcta
    }

    @Override
    public void destroy() {
    }
    
    private static PublicKey loadPublicKey(String filename) throws Exception {
        String key = new String(Files.readAllBytes(new File(filename).toPath()));
        
        // Eliminar les línies d'encapçalament i peu
        key = key.replace("-----BEGIN PUBLIC KEY-----", "")
                 .replace("-----END PUBLIC KEY-----", "")
                 .replaceAll("\\s", ""); // Elimina espais i salts de línia

        byte[] keyBytes = Base64.getDecoder().decode(key);

        X509EncodedKeySpec keySpec = new X509EncodedKeySpec(keyBytes);
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");

        return keyFactory.generatePublic(keySpec);
    }    
}
