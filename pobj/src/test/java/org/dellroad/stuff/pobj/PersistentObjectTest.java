
/*
 * Copyright (C) 2022 Archie L. Cobbs. All rights reserved.
 */

package org.dellroad.stuff.pobj;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

import org.dellroad.stuff.test.TestSupport;
import org.jibx.extras.DocumentComparator;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import org.springframework.util.StringUtils;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class PersistentObjectTest extends TestSupport {

    protected ClassPathXmlApplicationContext context;

    private String testName;
    private URL expectedXML;

    @BeforeMethod
    public void openContext(Method method) throws Exception {
        assert this.context == null;

        // Get test name
        this.testName = StringUtils.uncapitalize(method.getName().substring(4));

        // Get expected final result
        this.expectedXML = this.getClass().getResource(this.testName + ".out.xml");
        assert this.expectedXML != null : "didn't find resource: " + this.testName + ".out.xml";

        // Open application context
        boolean expectError = this.testName.contains("error");
        try {
            this.context = new ClassPathXmlApplicationContext(this.testName + ".xml", this.getClass());
            assert !expectError : "expected error but didn't get one";
        } catch (Exception e) {
            if (!expectError)
                throw e;
        }

        // Copy intial file content into place
        PersistentObject<?> pobj = this.context.getBean(PersistentObject.class);
        Files.copy(this.getClass().getResource(this.testName + ".pobj.xml").openStream(),
          pobj.getFile().toPath(), StandardCopyOption.REPLACE_EXISTING);

        // Start up persistent object
        pobj.start();
    }

    @AfterMethod(alwaysRun = true)
    public void closeContext(Method method) {
        if (this.context != null) {
            this.context.close();
            this.context = null;
        }
        this.testName = null;
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testBasic() throws Exception {

        // Get schema updater
        PersistentObjectSchemaUpdater<?> updater = this.context.getBean(PersistentObjectSchemaUpdater.class);

        // Get persistent object
        PersistentObject<RootObject> pobj = (PersistentObject<RootObject>)this.context.getBean(PersistentObject.class);
        assert pobj.getFile().exists();
        this.log.info("initial file:\n" + new String(Files.readAllBytes(pobj.getFile().toPath())));
        new File(pobj.getFile() + ".1").delete();
        new File(pobj.getFile() + ".2").delete();

        // Make changes
        RootObject root = pobj.getRoot();
        long version = pobj.getVersion();
        root.setName(root.getName() + ".new");
        root.setVerbose(!root.isVerbose());

        // Verify root was copied when read
        assert pobj.getRoot().isVerbose() != root.isVerbose() : "root not copied when read";

        // Test validation exception
        String temp = root.getName();
        root.setName(null);
        try {
            pobj.setRoot(root, version);
            throw new RuntimeException("expected validation exception");
        } catch (PersistentObjectValidationException e) {
            // expected
        }
        root.setName(temp);

        // Write it back
        pobj.setRoot(root, version);
        Thread.sleep(500);                      // wait for write-back to complete
        this.log.info("new file:\n" + new String(Files.readAllBytes(pobj.getFile().toPath())));

        // Verify value was copied correctly
        assert pobj.getRoot().equals(root) : "what got set is not what I wrote";

        // Verify root was copied when written
        root.setVerbose(!root.isVerbose());
        assert pobj.getRoot().isVerbose() != root.isVerbose() : "root not copied when written";

        // Verify backup was made
        assert new File(pobj.getFile() + ".1").exists();
        assert !new File(pobj.getFile() + ".2").exists();

        // Test optimistic lock excception
        try {
            pobj.setRoot(root, version);
            throw new RuntimeException("expected lock exception for version " + version + " != " + pobj.getVersion());
        } catch (PersistentObjectVersionException e) {
            // expected
        }

        // Verify value did not get set
        assert !pobj.getRoot().equals(root) : "value changed unexpectedly";

        // Verify result
        this.log.info("actual file:\n" + new String(Files.readAllBytes(pobj.getFile().toPath())));
        //this.log.info("expected file:\n" + new String(this.expectedXML.openStream()));
        InputStreamReader reader1 = new InputStreamReader(new FileInputStream(pobj.getFile()), "UTF-8");
        InputStreamReader reader2 = new InputStreamReader(this.expectedXML.openStream(), "UTF-8");
        assert new DocumentComparator(System.out, false).compare(reader1, reader2) : "different XML results";
        reader1.close();
        reader2.close();
    }

    //@Test
    public void testErrorUpdate() throws Exception {
    }
}
