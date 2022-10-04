# solr_analyzed_string -- fill a solr string based on a textField definition

There's a real advantage to doing data munging within the solr process, 
since you're guaranteed that index- and query-time analysis will be 
identical.

Unfortunately, solr `TextField` types aren't run through the analyzer
when doing range queries, and getting an exact phrase match (as opposed
to an exact _subphrase_ match) can be difficult.

This code allows you to create a `fieldType` based on a `solr.TextField`, 
and then create a solr `string` field with the results of running the
input through that set of filters.

## Usage

First, put the latest release .jar somewhere your schema will find it.

Then add something like this to your schema.

```xml
<!-- A type defined with keywordTokenizer that does the work -->
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

  <!-- A string type that will run the analyzer associated with the given 
  fieldType -->
  <fieldType name="browse_key" class="com.billdueber.solr.schema.AnalyzedString" fieldType="browse_key_analysis"/>

  <!-- A field that uses that type -->
  <field name="author_browse" type="browse_key" indexed="true" stored="true"/>

```
