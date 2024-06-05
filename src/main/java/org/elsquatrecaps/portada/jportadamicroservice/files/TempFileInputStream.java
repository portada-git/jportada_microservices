package org.elsquatrecaps.portada.jportadamicroservice.files;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;

/**
 *
 * @author josep
 */
public class TempFileInputStream extends FileInputStream{
    File tmpFIle;
    
    public TempFileInputStream(String f) throws FileNotFoundException {
        super(f);
        tmpFIle = new File(f);
        
    }
    
    public TempFileInputStream(File f) throws FileNotFoundException {
        super(f);
        tmpFIle = f;
    }

    @Override
    public void close() throws IOException {
        super.close(); 
//        try {
//            Thread.sleep(500);
//        } catch (InterruptedException ex) {
//            Logger.getLogger(TempFileInputStream.class.getName()).log(Level.SEVERE, null, ex);
//        }
        tmpFIle.delete();
    }
    
    
}
