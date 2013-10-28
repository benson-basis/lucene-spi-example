/******************************************************************************
 ** This data and information is proprietary to, and a valuable trade secret
 ** of, Basis Technology Corp.  It is given in confidence by Basis Technology
 ** and may only be used as permitted under the license agreement under which
 ** it has been distributed, and in no other way.
 **
 ** Copyright (c) 2013 Basis Technology Corporation All rights reserved.
 **
 ** The technical data and information provided herein are provided with
 ** `limited rights', and the computer software provided herein is provided
 ** with `restricted rights' as those terms are defined in DAR and ASPR
 ** 7-104.9(a).
 ******************************************************************************/

package com.basistech.relevance;

import com.google.common.base.Charsets;
import com.google.common.collect.Lists;
import org.apache.commons.io.IOUtils;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.TypeAttribute;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.Reader;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Command line to AnalyzerFactory experiments
 */
public final class Driver {
    private String artifactSpec;
    private List<ComponentSpec> charFilterSpecs = Lists.newArrayList();
    private ComponentSpec tokenizerSpec;
    private List<ComponentSpec> tokenFilterSpecs = Lists.newArrayList();
    private ComponentSpec currentCharFilterSpec;
    private ComponentSpec currentTokenFilterSpec;
    private boolean inTokenizerSpec;
    private AnalyzerFactory analyzerFactory;
    private String inputFile;
    private String outputFile;

    private Driver() {
        //
    }

    public static void main(String[] args) {
        if (args.length < 2) {
            System.err.println("Usage: Driver inputFile outputFile group:artifact:version [-charfilter charfilter opt=val opt=val -char ... ] tokenizer opt=val opt=val ... [-tokenfilter tokenfilters ...]");
            return;
        }

        Driver that = new Driver();
        that.parseArgs(args);
        that.setupFactory();
        try {
            that.processData();
        } catch (IOException e) {
            System.err.println("IO error");
        }
    }

    private void processData() throws IOException {
        Reader input = null;
        PrintWriter writer = null;
        try {
            input = new InputStreamReader(new FileInputStream(inputFile), Charsets.UTF_8);
            writer = new PrintWriter(new OutputStreamWriter(new FileOutputStream(outputFile), Charsets.UTF_8));
            Analyzer analyzer = analyzerFactory.newAnalyzer();
            TokenStream tokens = analyzer.tokenStream("dummy", input);
            CharTermAttribute charTerm = tokens.getAttribute(CharTermAttribute.class);
            TypeAttribute type = tokens.getAttribute(TypeAttribute.class);
            while (tokens.incrementToken()) {
                writer.append(charTerm.toString());
                writer.append("\t");
                writer.append(type.type());
                writer.append("\n");
            }
        } finally {
            IOUtils.closeQuietly(input);
            IOUtils.closeQuietly(writer);
        }
    }

    private void setupFactory() {
        analyzerFactory = new AnalyzerFactory();
        analyzerFactory.setArtifacts(Lists.newArrayList(artifactSpec));
        analyzerFactory.setCharFilterSpecs(charFilterSpecs);
        analyzerFactory.setTokenFilterSpecs(tokenFilterSpecs);
        analyzerFactory.setTokenizerSpec(tokenizerSpec);
        analyzerFactory.initialize();
    }

    private void closeOutCurrent() {
        if (currentCharFilterSpec != null) {
            charFilterSpecs.add(currentCharFilterSpec);
            currentCharFilterSpec = null;
        } else if (currentTokenFilterSpec != null) {
            tokenFilterSpecs.add(currentTokenFilterSpec);
            currentTokenFilterSpec = null;
        } else if (inTokenizerSpec) {
            inTokenizerSpec = false;
        }
    }

    private void requireAnotherArg(int argx, String[] args) {
        if (argx == args.length - 1) {
            System.err.println("Missing argument");
            System.err.println("Usage: Driver group:artifact:version [-charfilter charfilter opt=val opt=val -char ... ] -tokenizer tokenizer opt=val opt=val ... [-tokenfilter tokenfilters ...]");
            System.exit(1);
        }
    }

    private void parseArgs(String[] args) {
        inputFile = args[0];
        outputFile = args[1];
        artifactSpec = args[2];
        for (int argx = 3; argx < args.length; argx++) {
            String arg = args[argx];
            if ("-charfilter".equals(arg)) {
                closeOutCurrent();
                requireAnotherArg(argx, args);
                arg = args[++argx];
                currentCharFilterSpec = new ComponentSpec(arg, new HashMap<String, String>());
                charFilterSpecs.add(currentCharFilterSpec);
            } else if (currentCharFilterSpec != null) {
                addOption(currentCharFilterSpec.getOptions(), arg);
            } else if ("-tokenfilter".equals(arg)) {
                closeOutCurrent();
                requireAnotherArg(argx, args);
                arg = args[++argx];
                currentTokenFilterSpec = new ComponentSpec(arg, new HashMap<String, String>());
                tokenFilterSpecs.add(currentTokenFilterSpec);
            } else if (currentTokenFilterSpec != null) {
                addOption(currentTokenFilterSpec.getOptions(), arg);
            } else if ("-tokenizer".equals(arg)) {
                if (tokenizerSpec != null) {
                    System.err.println("Only one tokenizer.");
                    System.exit(1);
                }
                closeOutCurrent();
                requireAnotherArg(argx, args);
                arg = args[++argx];
                tokenizerSpec = new ComponentSpec(arg, new HashMap<String, String>());
                inTokenizerSpec = true;
            } else if (inTokenizerSpec) {
                addOption(tokenizerSpec.getOptions(), arg);
            }
        }
        closeOutCurrent();
    }

    private void addOption(Map<String, String> options, String arg) {
        String[] pieces = arg.split("=");
        options.put(pieces[0], pieces[1]);
    }

}
