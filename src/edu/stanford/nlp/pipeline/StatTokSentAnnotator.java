package edu.stanford.nlp.pipeline;

import edu.stanford.nlp.process.stattok.*;

import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreAnnotation;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.Annotator;
import edu.stanford.nlp.util.ArrayCoreMap;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.ArraySet;
import edu.stanford.nlp.util.PropertiesUtils;
import edu.stanford.nlp.util.StringUtils;
import edu.stanford.nlp.classify.ColumnDataClassifier;
import java.io.*;
import java.util.*;
import java.util.regex.*;


/**
 * This class performs the hybrid tokenization of the input.
 * It takes as properties a model for statistical tokenization and a file
 * containing tokens that will be tokenized via rules.
 * As for the original TokeinzerAnnotator in Stanford CoreNLP, it assumes that
 * the original text is in the CoreAnnotations.TextAnnotation field.
 * The class returns the same annotation as the original TokenizerAnnotator.
 *
 * @author Alessandro Bondielli
*/

public class StatTokSentAnnotator implements Annotator{

  StatTokSent statTokSent;

  /** The main method to intialize a tokenizer object.*/
  public StatTokSentAnnotator(String name, Properties props) throws IOException, ClassNotFoundException {
    // Get model and rule based tokens file paths from props
    String modelFile            = props.getProperty(name + ".model", null);
    String multiWordRulesFile   = props.getProperty(name + ".multiWordRules", null);
    int windowSize              = Integer.parseInt(props.getProperty(name + ".windowSize", "4")); //must reflect trained model.

    // If the model is not found, throws an exception.
    // If the multi-word tokens file is not found, initialize tokenizer with empty map
    if (modelFile == null) {
      throw new IllegalArgumentException("Tokenization model was not specified in "+ props);
    }

    if (multiWordRulesFile != null){
      statTokSent = new StatTokSent(modelFile, multiWordRulesFile, windowSize);
    } else {
      statTokSent = new StatTokSent(modelFile, windowSize);
    }
  }

  /** Method that performs the actual tokenization of CoreAnnotation.TextAnnotation object*/
  public void annotate(Annotation annotation){
    // Read text into string
    String text = annotation.get(CoreAnnotations.TextAnnotation.class);
    List<Character> chars = new ArrayList<Character>();

    // Find all line breaks (single and sequences) and multiple spaces in text and replace with a single character
    // Used for simplifying the tokenization process (line breaks are not tokens)

    // Preprocessing.
    List<String> lines = new ArrayList<String>(Arrays.asList(text.split("\n")));
    lines.replaceAll(line -> line.trim());
    lines.removeAll(Arrays.asList("", null));
    String textPreproc = String.join("\n", lines);

    String lineBreak = "[\\r\\n|\\r|\\n]+";
    String oneOrMoreSpace = "[ ]+";
    textPreproc = textPreproc.replaceAll(lineBreak, "\u00A7"); //replace all line breaks with § symbol
    textPreproc = textPreproc.replaceAll(oneOrMoreSpace, " ");
    if (textPreproc.substring(0,1) == "\u00A7"){
      textPreproc = textPreproc.substring(1);
    }

    // Annotator calls the tokenize method of StatTokSent.
    // It returns a list of Sentences. Each Sentece is a List of CoreLabels, each representing a single Token.
    List<List<CoreLabel>> sTokens = statTokSent.tokenize(textPreproc);

    List<CoreMap> sentences = new ArrayList<>();
    ArrayList<CoreLabel> tokens = new ArrayList<>();
    int sIndex = 0;
        
    // Add required annotations for each sentence in sTokens.
    // Add all sentences and tokens information to the Annotation
    for (List<CoreLabel> sentence : sTokens) {
      int tokenIndex = 1;
      if (sentence.size() == 0) {
        continue;
      }
      CoreMap sent = new ArrayCoreMap(1);

      int begin = sentence.get(0).beginPosition();
      int end = sentence.get(sentence.size() - 1).endPosition();

      sent.set(CoreAnnotations.TokensAnnotation.class, sentence);

      sent.set(CoreAnnotations.SentenceIndexAnnotation.class, sIndex++);
      sent.set(CoreAnnotations.CharacterOffsetBeginAnnotation.class, begin);
      sent.set(CoreAnnotations.CharacterOffsetEndAnnotation.class, end);

      sent.set(CoreAnnotations.TokenBeginAnnotation.class, tokenIndex);
      tokenIndex += sentence.size();
      sent.set(CoreAnnotations.TokenEndAnnotation.class, tokenIndex);
      sent.set(CoreAnnotations.TextAnnotation.class, text.substring(begin, end+1));

      sentences.add(sent);
      tokens.addAll(sentence);
    }
    annotation.set(CoreAnnotations.TokensAnnotation.class, tokens);
    annotation.set(CoreAnnotations.SentencesAnnotation.class, sentences);
  }

  @Override
  public Set<Class<? extends CoreAnnotation>> requires() {
    return Collections.emptySet();
  }

  @Override
  public Set<Class<? extends CoreAnnotation>> requirementsSatisfied() {
    return new HashSet<>(Arrays.asList(CoreAnnotations.TextAnnotation.class,
                                       CoreAnnotations.TokensAnnotation.class,
                                       CoreAnnotations.CharacterOffsetBeginAnnotation.class,
                                       CoreAnnotations.CharacterOffsetEndAnnotation.class,
                                       CoreAnnotations.BeforeAnnotation.class,
                                       CoreAnnotations.AfterAnnotation.class,
                                       CoreAnnotations.TokenBeginAnnotation.class,
                                       CoreAnnotations.TokenEndAnnotation.class,
                                       CoreAnnotations.PositionAnnotation.class,
                                       CoreAnnotations.IndexAnnotation.class,
                                       CoreAnnotations.OriginalTextAnnotation.class,
                                       CoreAnnotations.ValueAnnotation.class,
                                       CoreAnnotations.SentenceIndexAnnotation.class,
                                       CoreAnnotations.SentencesAnnotation.class,
                                       CoreAnnotations.DocIDAnnotation.class
                                       ));
  }
}
