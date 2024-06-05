/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package org.elsquatrecaps.portada.jportadamicroservice;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.logging.Level;
import java.util.logging.Logger;
import lombok.Data;
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
    
    @GetMapping(path = "/test", produces = "application/json")
    public Message test(){
        return new Message("Portada API is working");
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
    
    @PostMapping( path = "/ocr")
    public String processOcr(@RequestParam("team") String team,  @RequestParam("image") MultipartFile file){
        String ret;
        FileAndExtension tmpImage  = saveTmpImage(file);
        ProcessOcrDocument processor = new ProcessOcrDocument();
        try {
            processor.init(new File("/etc/.document_ai/").getCanonicalFile().getAbsolutePath(), team);
            processor.setFilePath(tmpImage.getFile().getAbsolutePath());
            processor.process();
            ret = processor.getText();
            tmpImage.getFile().delete();
        } catch (IOException ex) {
            ret = "";
            Logger.getLogger(PortadaApi.class.getName()).log(Level.SEVERE, null, ex);
        }
        return ret;        
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
