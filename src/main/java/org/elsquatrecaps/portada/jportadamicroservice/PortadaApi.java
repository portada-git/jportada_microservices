package org.elsquatrecaps.portada.jportadamicroservice;

import com.google.cloud.documentai.v1beta3.Document;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.mail.MessagingException;
import javax.mail.internet.AddressException;
import lombok.Data;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.text.RandomStringGenerator;
import org.elsquatrecaps.portada.jportadamicroservice.cypher.EncryptDecryptAes;
import org.elsquatrecaps.portada.jportadamicroservice.files.TempFileInputStream;
import org.elsquatrecaps.portada.portadaimagetools.FixBackTransparencyTool;
import org.elsquatrecaps.portada.portadaocr.ProcessOcrDocument;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 *
 * @author josep
 */
@RestController
public class PortadaApi {
    private static final char[][] CHAR_PAIRS = {{'0','9'},{'a','z'},{'_','_'},{'A','Z'}};
    //public static final MailSender mailSender=new MailSender(decryptFileToString("/etc/.jportada_microservices/gmail/portada.project.json", "IATNEMUCOD_TERCES"));
    public static MailSender mailSender;
    
    @GetMapping(path = "/test", produces = "application/json")
    public Message test(){
        return new Message("Portada API is working from JVM");
    }
    
    @PostMapping(path="/pr/acceptKey")
    public String acceptKey(@RequestParam("team") String team, @RequestParam("pkname") String pkname,  @RequestParam("u") String adminuser, @RequestParam("p") String adminpas){
        String ret;
        JsonObject users = JsonParser.parseString(decryptFileToString("/etc/.portada_microservices/portada.users.json", "ADATROP_TERCES")).getAsJsonObject();
        if(users.get(adminuser)!=null 
                && users.get(adminuser).getAsJsonObject().get("role")!=null 
                && users.get(adminuser).getAsJsonObject().get("role").getAsString().equals("admin") 
                && users.get(adminuser).getAsJsonObject().get("pas")!=null
                && (users.get(adminuser).getAsJsonObject().get("pas").getAsString().equals(adminpas)
                        || DigestUtils.md5Hex(users.get(adminuser).getAsJsonObject().get("pas").getAsString()).toUpperCase().equals(adminpas))){                
            String verifiedKeyFileName = String.format("/etc/.portada_microservices/%s/verifiedAccessKeys/%s", team, pkname);
            String acceptedKeyFileName = String.format("/etc/.portada_microservices/%s/approvedAccessKeys/%s", team, pkname);
            File verifiedKeyFile = new File(verifiedKeyFileName);
            File acceptedKeyFile = new File(acceptedKeyFileName);
            if(verifiedKeyFile.getParentFile().exists()){
                if(!acceptedKeyFile.getParentFile().exists()){
                    acceptedKeyFile.getParentFile().mkdirs();
                }      
                boolean res = verifiedKeyFile.renameTo(acceptedKeyFile);
                if(res){
                    //ok
                    String email = pkname.substring(0, pkname.length()-20);
                    email = email.replaceAll("__AT_SIGN__", "@");
                    users.add( email, JsonParser.parseString("{\"role\":\"user\"}").getAsJsonObject());
                    encryptStringToFile("/etc/.portada_microservices/portada.users.json", "ADATROP_TERCES", users.toString());
                    ret = "{\"statusCode\":0, \"message\":\"The access key was approved. A new user has access to PAPI\"}";
                }else{
                    //error no s'ha pogut renombrar la clau verificada
                    ret = "{\"statusCode\":1, \"message\":\"ERROR: There was some problem renaming the access key.\"}";
                }
            }else{
                //error no existeix la clau verificada
                ret = "{\"statusCode\":2, \"message\":\"ERROR: The access key to be approved doesn't exist.\"}";
            }                  
        }else{
            //error usuari incorrecte o sense privilegis
            ret = "{\"statusCode\":3, \"message\":\"ERROR: User or password incorrect or user without privileges.\"}";
        }
        return ret;
    }
    
    @PostMapping(path="/pr/deleteKey")
    public String deleteKey(@RequestParam("team") String team, @RequestParam("pkname") String pkname,  @RequestParam("u") String adminuser, @RequestParam("p") String adminpas){
        String ret;
        JsonObject users = JsonParser.parseString(decryptFileToString("/etc/.portada_microservices/portada.users.json", "ADATROP_TERCES")).getAsJsonObject();
                if(users.get(adminuser)!=null 
                && users.get(adminuser).getAsJsonObject().get("role")!=null 
                && users.get(adminuser).getAsJsonObject().get("role").getAsString().equals("admin") 
                && users.get(adminuser).getAsJsonObject().get("pas")!=null
                && (users.get(adminuser).getAsJsonObject().get("pas").getAsString().equals(adminpas)
                        || DigestUtils.md5Hex(users.get(adminuser).getAsJsonObject().get("pas").getAsString()).toUpperCase().equals(adminpas))){
            String verifiedKeyFileName = String.format("/etc/.portada_microservices/%s/verifiedAccessKeys/%s", team, pkname);
            File verifiedKeyFile = new File(verifiedKeyFileName);
            if(verifiedKeyFile.getParentFile().exists()){
                boolean res = verifiedKeyFile.delete();
                if(res){
                    //ok
                    ret = "{\"statusCode\":0, \"message\":\"The access key was deleted.\"}";
                }else{
                    //error no s'ha pogut eleminar la clau verificada
                    ret = "{\"statusCode\":1, \"message\":\"ERROR: There was some problem deleting the access key. Please check the system files\"}";
                }
            }else{
                //error no existeix la clau verificada
                ret = "{\"statusCode\":2, \"message\":\"ERROR: The access key to be deleted doesn't exist.\"}";
            }                  
        }else{
            //error usuari incorrecte o sense privilegis
            ret = "{\"statusCode\":3, \"message\":\"ERROR: User or password incorrect or user without privileges.\"}";
        }
        return ret;
    }
    
    @PostMapping( path = "/verifyRequestedAcessPermission")
    public String verifyRequestedAccessPermission(@RequestParam("team") String team, @RequestParam("email") String email, @RequestParam("code") String code){
        String ret=null;
        String messageForOk = null;
        String emailForPath = email.replaceAll("@", "__AT_SIGN__");
        String pathbase = String.format("/etc/.portada_microservices/%s/requestedAccessKeys/%s", team, emailForPath);
        String path = String.format("%s/%s/", pathbase, code);
        deleteOldAccessRequest("/etc/.portada_microservices/", "requestedAccessKeys");            
        Path p = Paths.get(path);
        boolean existKeyPath =true;
        if(Files.exists(p)){
            try {
                Optional<Path> keyPath;
                keyPath = Files.list(p).findFirst();
                if(!keyPath.isEmpty()){
                    String verifiedDir;
                    String mailMessageForAdminTemplate;
                    JsonObject users = JsonParser.parseString(decryptFileToString("/etc/.portada_microservices/portada.users.json", "ADATROP_TERCES")).getAsJsonObject();
                    if(users.get(email)!=null){
                        //copiar la clau que hi ha en el directori
                        verifiedDir = String.format("/etc/.portada_microservices/%s/approvedAccessKeys/", team);
                        mailMessageForAdminTemplate = "The file %s belonging to team %s was approved because the e-mail associated was registered. Revise id and delete it i was necessari. Data is:\n\n-k %s -tm %s";
                        messageForOk = "The access request was verified. You should already be able to access PAPI. If not, please contact with de PAPI administrator.";
                    }else{
                        //copiar la clau que hi ha en el directori
                        verifiedDir = String.format("/etc/.portada_microservices/%s/verifiedAccessKeys/", team);
                        mailMessageForAdminTemplate = "The file %s belonging to team %s was verified . Accept it o delete it. Data is:\n\n-k %s -tm %s";
                        messageForOk = "The access request was verified. You will be able to access PAPI in a few days";                        
                    }
                    File keyfile = keyPath.get().toFile();
                    String verifiedKeyFileName = String.format("%s_%s_%s" , emailForPath, code, keyfile.getName());
                    File verifiedKeyFile = new File(verifiedDir, verifiedKeyFileName);
                    if(!verifiedKeyFile.getParentFile().exists()){
                        verifiedKeyFile.getParentFile().mkdirs();
                    }
                    boolean res = keyfile.renameTo(verifiedKeyFile);
                    if (res){
                        keyfile.getParentFile().delete();
                        if(keyfile.getParentFile().getParentFile().list().length==0){
                            keyfile.getParentFile().getParentFile().delete();
                        }
                        //enviar correu de confirmació a l'admin
                        if(mailSender==null){
                            mailSender=new MailSender(decryptFileToString("/etc/.portada_microservices/gmail/portada.project.json", "LIAMG_TERCES"));
//                            mailSender = new MailSender(Files.readString(Paths.get("/etc/.jportada_microservices/gmail/portada.project.json")));
                        }
                        try {
                            mailSender.sendMessageToAdmin("Access to PAPI request", String.format(mailMessageForAdminTemplate, verifiedKeyFileName, team, verifiedKeyFileName, team));
                        } catch (MessagingException ex) {
                            //Proces correcte a excepciço de l'avís a l'adminimitrador. Cal avisar manualment a l'administrador.
                            ret = "{\"statusCode\":2, \"message\":\"Verification was successful, but there was an error trying to notify the administrator. You do not need to re-apply for access, but please email the administrator directly so that your permission can take effect.\"}";
                        }
                    }else{
//                        throw new RuntimeException("ERROR copying public key file. Pleass contant with admin PAPI");
                        //Error no s'ha copiat a la carpeta de verificats. S'eliminarà automàticament. Cal repetir el procés o avisar a l'admnistrador
                        ret = "{\"statusCode\":3, \"message\":\"Verification was successful, but there was an error handling the permission and it will not take effect. Try the request again. If this error occurs again, notify your administrator.\"}";
                    }
                }else{
                    existKeyPath=false;
                }            
            } catch (IOException ex) {
                existKeyPath = false;
            }
        }else{
            existKeyPath = false;
        }
        if(existKeyPath && ret==null){
            //error message indicant team, email o codi incorrecte o temps expirat!
            ret = "{\"statusCode\":0, \"message\":\"".concat(messageForOk).concat("\"}");
        }else if(ret==null){
            //error message indicant team, email o codi incorrecte o temps expirat!
            ret = "{\"statusCode\":1, \"message\":\"ERROR: The access request could't be verified. Maybe there is some incorrect data or the time has expired. Please resend a new request for accesing to PAPI.\"}";            
        }
       return ret;
    }

    @PostMapping( path = "/requestAccessPermission")
    public String requestAccessPermission(@RequestParam("team") String team, 
                                            @RequestParam("email") String email, 
                                            @RequestParam("pk") MultipartFile publicKey,
                                            @RequestParam(defaultValue="", name="oldKeyName", required=false) String oldKeyName){
        String ret=null;
        //String randomName = RandomStringGenerator.builder().get().generate(30);
        String emailForPath = email.replaceAll("@", "__AT_SIGN__");
        String randomCode = RandomStringGenerator.builder().withinRange(CHAR_PAIRS).get().generate(8);
        String pathbase = String.format("/etc/.portada_microservices/%s/requestedAccessKeys/%s", team, emailForPath);        
        String filename = String.format("%s/%s/%s", pathbase, randomCode, /*randomName,*/ publicKey.getOriginalFilename());
        try {
            if(oldKeyName!=null && !oldKeyName.trim().isEmpty()){
                //delete in verified or approved
                File keyTodelete = new File(String.format("/etc/.portada_microservices/%s/verifiedAccessKeys/%s", team, oldKeyName));
                if(keyTodelete.exists()){
                    keyTodelete.delete();
                }
                keyTodelete = new File(String.format("/etc/.portada_microservices/%s/approvedAccessKeys/%s", team, oldKeyName));
                if(keyTodelete.exists()){
                    keyTodelete.delete();
                }
            }
            //eliminar fitxers antics
            deleteOldAccessRequest("/etc/.portada_microservices/", "requestedAccessKeys");            
            //guardar a clau
            saveFile(publicKey, filename);
            //enviar el codi per email
            if(mailSender==null){
                mailSender=new MailSender(decryptFileToString("/etc/.portada_microservices/gmail/portada.project.json", "LIAMG_TERCES"));
//                mailSender = new MailSender(Files.readString(Paths.get("/etc/.jportada_microservices/gmail/portada.project.json")));
            }
            mailSender.sendMessageToMailAdress("Access to PAPI verification", String.format("%s is the PAPI verification code. Copy it and submit it to verify your access request before time expires.", randomCode), email);
            ret = "{\"statusCode\":0, \"message\":\"Request to access to PAPI was sent. You will receive an e-mail with a verification code. Send the code to verify your request\"}";
        } catch (AddressException ex) {
            Logger.getLogger(PortadaApi.class.getName()).log(Level.SEVERE, null, ex);
            File pkFile = new File(filename);
            pkFile.delete();
            ret = "{\"statusCode\":1, \"message\":\"".concat(String.format("ERROR: %s", ex.getMessage())).concat("Please revise your email address and resend a new request for accesing to PAPI.\"}");            
        } catch (IOException | MessagingException ex) {
            Logger.getLogger(PortadaApi.class.getName()).log(Level.SEVERE, null, ex);
            File pkFile = new File(filename);
            pkFile.delete();
            ret = "{\"statusCode\":1, \"message\":\"".concat(String.format("ERROR: %s", ex.getMessage())).concat("Please resend a new request for accesing to PAPI.\"}");            
        }
        return ret;
    }
    
    @PostMapping( path = "/fixBackTransparency")
    @ResponseBody
    public ResponseEntity<byte[]> fixTransparency(@RequestParam("image") MultipartFile file){
        byte[] ret;
        FileAndExtension tmpImage  = saveTmpImage(file);

        FixBackTransparencyTool prg = new FixBackTransparencyTool();
        prg.setImagePath(tmpImage.getFile().getAbsolutePath());
        prg.fixTransparency();
        prg.saveImage(tmpImage.getFile().getAbsolutePath());
        MediaType contentMediaType;
        switch (tmpImage.getExtension().toLowerCase()) {
            case ".jpeg":
            case ".jpg":
                contentMediaType = MediaType.IMAGE_JPEG;
                break;
            case ".png":
                contentMediaType = MediaType.IMAGE_PNG;
                break;
            case ".gif":
                contentMediaType = MediaType.IMAGE_GIF;
                break;
            case ".tif":
            case ".tiff":
                contentMediaType = MediaType.valueOf("image/tiff");
                break;
            default:
                contentMediaType = MediaType.IMAGE_JPEG;
        }
//        try(InputStream in = new FileInputStream(tmpImage.getFile())){
        try(InputStream in = new TempFileInputStream(tmpImage.getFile())){
            ret = in.readAllBytes();
        } catch (IOException ex) {
            ret = new byte[0];
            Logger.getLogger(PortadaApi.class.getName()).log(Level.SEVERE, null, ex);
        }
    return ResponseEntity.ok()
      .contentType(contentMediaType)
      .body(ret);
    }
    
    @PostMapping( path = "/pr/ocr")
    public String processOcr(@RequestParam("team") String team,  @RequestParam("image") MultipartFile file){
        String ret;
        FileAndExtension tmpImage  = saveTmpImage(file);
//        ProcessOcrDocument processor = new ProcessOcrDocument();
        try {            
            ProcessOcrDocument processor = __runAndGetprocessOcr(team, tmpImage);
//            processor.init(new File("/etc/.portada_microservices/").getCanonicalFile().getAbsolutePath(), team);
//            processor.setCredentialsStream(decryptFileToStream(processor.getCredentialsPath()));
//            processor.setFilePath(tmpImage.getFile().getAbsolutePath());
//            processor.process();
            ret = processor.getText();
            tmpImage.getFile().delete();
        } catch (IOException ex) {
            ret = "";
            Logger.getLogger(PortadaApi.class.getName()).log(Level.SEVERE, null, ex);
        }
        return ret;        
    }

    @PostMapping( path = "/pr/ocrDocument")
    public Document processOcrDocument(@RequestParam("team") String team,  @RequestParam("image") MultipartFile file){
        Document ret;
        FileAndExtension tmpImage  = saveTmpImage(file);
//        ProcessOcrDocument processor = new ProcessOcrDocument();
        try {            
            ProcessOcrDocument processor = __runAndGetprocessOcr(team, tmpImage);
//            processor.init(new File("/etc/.portada_microservices/").getCanonicalFile().getAbsolutePath(), team);
//            processor.setCredentialsStream(decryptFileToStream(processor.getCredentialsPath()));
//            processor.setFilePath(tmpImage.getFile().getAbsolutePath());
//            processor.process();
            ret = processor.getResult();
            tmpImage.getFile().delete();
        } catch (IOException ex) {
            ret = null;
            Logger.getLogger(PortadaApi.class.getName()).log(Level.SEVERE, null, ex);
        }
        return ret;        
    }
    
    @PostMapping( path = "/pr/ocrJson")
    public String processOcrJson(@RequestParam("team") String team,  @RequestParam("image") MultipartFile file){
        String ret;
        FileAndExtension tmpImage  = saveTmpImage(file);
//        ProcessOcrDocument processor = new ProcessOcrDocument();
        try {            
            ProcessOcrDocument processor = __runAndGetprocessOcr(team, tmpImage);
            ret = processor.getJsonString();
            tmpImage.getFile().delete();
        } catch (IOException ex) {
            ret = "";
            Logger.getLogger(PortadaApi.class.getName()).log(Level.SEVERE, null, ex);
        }
        return ret;        
    }
    
    private ProcessOcrDocument __runAndGetprocessOcr(String team,  FileAndExtension tmpImage) throws IOException{
        ProcessOcrDocument processor = new ProcessOcrDocument();
        processor.init(new File("/etc/.portada_microservices/").getCanonicalFile().getAbsolutePath(), team);
        processor.setCredentialsStream(decryptFileToStream(processor.getCredentialsPath()));
        processor.setFilePath(tmpImage.getFile().getAbsolutePath());
        processor.process();
        return processor;        
    }
//    private static String decryptFileToString(String path){
//        return decryptFileToString(path, "IATNEMUCOD_TERCES");
//    }
    
    private static String decryptFileToString(String path, String secretEnvironment){
        String ret = null;
        try {
            ret = inputStreamToString(decryptFileToStream(path, secretEnvironment));
        } catch (IOException ex) {
            Logger.getLogger(PortadaApi.class.getName()).log(Level.SEVERE, null, ex);
        }
        return ret;
    }
    
    private static String inputStreamToString(InputStream inputStream) throws IOException{
        StringBuilder textBuilder = new StringBuilder();
        try (Reader reader = new BufferedReader(new InputStreamReader
          (inputStream, StandardCharsets.UTF_8))) {
            int c;
            while ((c = reader.read()) != -1) {
                textBuilder.append((char) c);
            }
        }
        return textBuilder.toString();
    }


    private static InputStream decryptFileToStream(String path){
        return decryptFileToStream(path, "IATNEMUCOD_TERCES");
    }
    
    private static InputStream decryptFileToStream(String path, String secretEnvironment){
        EncryptDecryptAes decryptAes;
        String retDec;
        InputStream ret;
        try {
            decryptAes = new EncryptDecryptAes();
            retDec = decryptAes.decrypt(path, System.getenv(secretEnvironment));
            ret = new ByteArrayInputStream(retDec.getBytes());
        } catch (IOException | NoSuchAlgorithmException | InvalidKeySpecException | NoSuchPaddingException | InvalidKeyException | InvalidAlgorithmParameterException | IllegalBlockSizeException | BadPaddingException ex) {
            throw new RuntimeException(ex);
        }
        return ret;
    }
    
    private static boolean deleteOldAccessRequest(String path, String requestedAccessKeysDir){
        boolean ret = true;
        File base = new File(path);
        if(base.exists()){
            for(File f: base.listFiles()){
                File dirToDelete = new File(f, requestedAccessKeysDir);
                if(dirToDelete.exists()){
                    ret = deleteOldAccessRequest(dirToDelete.getAbsolutePath()) && ret;
                }
            }
        }
        return ret;
    }
    
    private static boolean deleteOldAccessRequest(String path){
        boolean ret = true;
        try {
            final ArrayList<String> errorFiles  = new ArrayList<>();
            if(Files.exists(Paths.get(path))){
                Files.list(Paths.get(path)).forEach((t) -> {
                    try {
                        if(Files.isDirectory(t)){
                            deleteOldAccessRequest(t.toString());
                            if(Files.list(t).findFirst().isEmpty()){
                                Files.delete(t);
                            }
                        }else{
                            if(Files.getLastModifiedTime(t).toInstant().plus(10, ChronoUnit.MINUTES).compareTo(Instant.now())<0){
                                Files.delete(t);
                            }
                        }
                    } catch (IOException ex) {
                        Logger.getLogger(PortadaApi.class.getName()).log(Level.SEVERE, null, ex);
                        errorFiles.add(t.toString());
                    }
                });
                ret = errorFiles.isEmpty();
            }
        } catch (IOException ex) {
            Logger.getLogger(PortadaApi.class.getName()).log(Level.SEVERE, null, ex);
            ret = false;
        }
        return ret;
    }

    private void saveFile(MultipartFile file, String path) throws FileNotFoundException, IOException{
        File filePath = new File(path);
        File parentFile = filePath.getParentFile();
        boolean dirsCreated=parentFile.exists();
        if(!dirsCreated){
            parentFile= filePath.getParentFile();
            dirsCreated = parentFile.mkdirs();
        }
        if(dirsCreated){
            List<File> unwritableDirs = new ArrayList<>();
            while(!parentFile.equals(new File("/etc/")) && !Files.getPosixFilePermissions(Paths.get(parentFile.getPath())).contains(PosixFilePermission.GROUP_WRITE)){
                unwritableDirs.add(parentFile);
                parentFile = parentFile.getParentFile();
            }
            for(int i=unwritableDirs.size()-1; i>=0; i--){
                Files.setPosixFilePermissions(unwritableDirs.get(i).toPath(), PosixFilePermissions.fromString("rwxrwxr--"));
            }
            try (OutputStream os = new FileOutputStream(filePath)) {
                os.write(file.getBytes());
            }
            Files.setPosixFilePermissions(filePath.toPath(), PosixFilePermissions.fromString("rw-rw-r--"));
        }else{
            //error
        }
    }
    
    private FileAndExtension saveTmpImage(MultipartFile file){
            File tmpImagePath=null;
            File tmpDir = new File("/tmp");
            if(!tmpDir.exists()){
                tmpDir.mkdirs();
            }
            String ext = file.getOriginalFilename().substring(file.getOriginalFilename().lastIndexOf("."));
//        File tmpImagePath = new File(tmpDir, "img".concat(ext));
        try {
            tmpImagePath = File.createTempFile("img", ext, tmpDir);
        } catch (IOException ex) {
            Logger.getLogger(PortadaApi.class.getName()).log(Level.SEVERE, null, ex);
        }    
        try (OutputStream os = new FileOutputStream(tmpImagePath)) {
            os.write(file.getBytes());
        } catch (IOException ex) {
            Logger.getLogger(PortadaApi.class.getName()).log(Level.SEVERE, null, ex);
        }
        return new FileAndExtension(tmpImagePath, ext);
    }

    private void encryptStringToFile(String path, String keyToEncript, String json) {
//        KeySpec spec = new PB(password.toCharArray(), salt, iterations, 256 + 128);
    }
    
    private static class FileAndExtension{
        File file;
        String extension;

        public FileAndExtension(File file, String extension) {
            this.file = file;
            this.extension = extension;
        }
        
        public File getFile(){
            return file;
        }

        public String getExtension(){
            return extension;
        }
    }
    
    
    @Data
    private static class Message{
        private final String message;

        public Message(String messate) {
            this.message = messate;
        }

        /**
         * @return the message
         */
        public String getMessage() {
            return message;
        }        
    }
    
}
