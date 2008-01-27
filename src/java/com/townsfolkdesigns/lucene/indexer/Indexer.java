/*
 * Copyright (c) 2008 Eric Berry <elberry@gmail.com>
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package com.townsfolkdesigns.lucene.indexer;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.apache.lucene.document.Document;
import org.apache.lucene.index.IndexWriter;

import java.io.File;
import java.io.FileFilter;

import java.util.Date;


/**
 * An Indexer indexes files, and converts them into Documents to be added to the
 * IndexWriter. Each Indexer only needs to implement the indexFile method,
 * traversing directories, opening and closing the IndexWriter is all done by
 * this abstract class.
 *
 * @author eberry
 */
public abstract class Indexer implements Runnable {
   private int directoriesIndexed = 0;
   private FileFilter fileFilter;
   private int filesIndexed = 0;
   private String indexStore;
   private IndexWriter indexWriter;
   private String[] locations;
   private Log log = LogFactory.getLog(getClass());
   private boolean recursivelyIndexDirectoriesOn;

   public Indexer() {
      String currentDirectory = System.getProperty("user.dir");
      File indexStoreDirectory = new File(currentDirectory, "indexes");
      File indexStoreFile = new File(indexStoreDirectory, "default");
      setIndexStore(indexStoreFile.getPath());
   }

   public FileFilter getFileFilter() {
      return fileFilter;
   }

   public String getIndexStore() {
      return indexStore;
   }

   public IndexWriter getIndexWriter() {
      return indexWriter;
   }

   public String[] getLocations() {
      return locations;
   }

   public boolean isRecursivelyIndexDirectoriesOn() {
      return recursivelyIndexDirectoriesOn;
   }

   public void run() {

      if (getIndexWriter() != null) {
         File locationFile = null;
         Document document = null;

         if (log.isInfoEnabled()) {
            log.info("Indexing started " + new Date() + " - locations found: " + getLocations().length);
         }

         for (String location : getLocations()) {
            locationFile = new File(location);

            if (locationFile.isDirectory()) {

               // the indexer will go in to any directories specified in
               // the locations field. However, if recursion is off,
               // only files within those top level directories will be
               // indexed.
               indexDirectory(locationFile);
            } else if (getFileFilter().accept(locationFile)) {

               // if the location file matches the file filter, then we
               // index it.
               document = countAndIndexFile(locationFile);

               if (document != null) {

                  try {
                     getIndexWriter().addDocument(document);
                  } catch (Exception e) {
                     log.error(e.getMessage(), e);
                  }
               }
            }
         }

         if (log.isInfoEnabled()) {
            log.info("Indexing completed " + new Date() + " - directories: " + directoriesIndexed + " | files: " +
               filesIndexed);
         }

         // now that we've indexed all the files and added them to the index
         // writer, let's optimize and close the writer.
         if (getIndexWriter() != null) {

            try {
               getIndexWriter().optimize();
               getIndexWriter().close();
            } catch (Exception ex) {
               log.error(ex.getMessage(), ex);
            }
         }
      } else {
         log.warn("No index writer provided.");
         System.exit(1);
      }
   }

   public void setFileFilter(FileFilter fileFilter) {
      this.fileFilter = fileFilter;
   }

   public void setIndexStore(String indexStore) {
      this.indexStore = indexStore;
   }

   public void setIndexWriter(IndexWriter indexWriter) {
      this.indexWriter = indexWriter;
   }

   public void setLocations(String[] locations) {
      this.locations = locations;
   }

   public void setRecursivelyIndexDirectoriesOn(boolean recursivelyIndexDirectoriesOn) {
      this.recursivelyIndexDirectoriesOn = recursivelyIndexDirectoriesOn;
   }

   /**
    * Each indexer only needs to be responsble for turning the given file in to
    * a Document and adding that document to the IndexWriter.
    *
    * @param file
    */
   protected abstract Document indexFile(File file);

   private Document countAndIndexFile(File locationFile) {
      filesIndexed++;

      return indexFile(locationFile);

   }

   private void indexDirectory(File directory) {
      directoriesIndexed++;

      File[] files = null;

      if (getFileFilter() != null) {
         files = directory.listFiles(getFileFilter());
      } else {
         files = directory.listFiles();
      }

      Document document = null;

      for (File file : files) {

         if (!file.isDirectory()) {
            document = countAndIndexFile(file);

            if (document != null) {

               try {
                  getIndexWriter().addDocument(document);
               } catch (Exception e) {
                  log.error(e.getMessage(), e);
               }
            }
         } else if (isRecursivelyIndexDirectoriesOn()) {
            indexDirectory(file);
         }
      }
   }
}
