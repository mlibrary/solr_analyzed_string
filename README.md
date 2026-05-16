# solr_analyzed_string -- fill a solr string based on a textField definition

tl;dr Allows you to run index and query string through a custom 
fieldType's analysis chain and store the result as a
derivitive of the `string` field, guaranteeing that range queries use the exact
same normalization as indexing.

There's a real advantage to doing data munging within the Solr process,
since you're guaranteed that index- and query-time analysis will be
identical.

Solr 10 `TextField` does apply analysis to range query bounds via an
automatically derived `multiTermAnalyzer`. However, that derived analyzer
is a subset of the full analysis chain — it extracts only normalizing
filters (lowercasing, ASCII folding, etc.) but cannot reliably replicate
complex transformations such as leading-article stripping or custom
`PatternReplaceFilter` chains.

See the Usage below, you'll end up with the following

1. a fieldType with the keyword tokenizer that contains the 
   analysis chain you want. It *must* use the keywordTokenizer.
2. a fieldType with the AnalyzedString class that reference #1
3. a field of the type created in #2


- make another fieldType of type Analyzed string that references the first
- Finally, create a field

- Create a fieldType (e.g, "MyAnalyzer") with the KeywordTokenizer and your desired
  analysis chain. It must use the keyword tokenizer.
- Create another fieldType (e.g, "IntermediateFieldType") with 
  class=com.billdueber.solr.schema.AnalyzedString
  that references the first fieldType attr fieldType="MyAnalyzer")
- Create a field of type 

So, we run the full analysis chain
(defined as a `solr.TextField` with a `KeywordTokenizer`) at index time
and storing the result as a (child class of a) plain `string` field. 
Range queries against that new field will now be translated at
query time by the same chain and work correctly.



This code allows you to create a `fieldType` based on a `solr.TextField`,
and then create a Solr `string` field with the results of running the
input through that set of filters.

## Usage

First, put the latest release .jar somewhere your schema will find it.

Then add something like this to your schema.

```xml
<!-- A type defined with keywordTokenizer that does the work

  A fieldType that does all sorts of violence to the input that changes it
  substantially

-->

  <fieldType name="browse_key_analysis" class="solr.TextField" positionIncrementGap="10000">
    <analyzer>
      <tokenizer class="solr.KeywordTokenizerFactory"/>
      <filter class="solr.ICUFoldingFilterFactory"/>
      <filter class="solr.PatternReplaceFilterFactory" pattern="^the\s+" replacement="" replace="all"/>
      <filter class="solr.PatternReplaceFilterFactory" pattern="[:\-]+" replacement=" " replace="all"/>
      <filter class="solr.PatternReplaceFilterFactory" pattern="[\p{P}\p{Sm}\p{So}]" replacement="" replace="all"/>
      <filter class="solr.TrimFilterFactory"/>
      <filter class="solr.PatternReplaceFilterFactory" pattern="\p{Z}" replacement=" " replace="all"/>
    </analyzer>
  </fieldType>

  <!-- An AnalyzedString type that will run that analysis chain -->

  <fieldType name="browse_key" 
             class="com.billdueber.solr.schema.AnalyzedString" 
             fieldType="browse_key_analysis"/>

  <!-- A field that uses that type. It's a subclass of solr.String and
       will behave like one, including in range queries.
  -->

  <field name="author_browse" type="browse_key" indexed="true" stored="true"/>

```
