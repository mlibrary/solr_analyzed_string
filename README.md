# solr_scaffold -- base classes and examples for solr filters and String-type field

There's a real advantage to doing data munging within the solr process, 
since you're guaranteed that index- and query-time analysis will be 
identical. Actually making custom filters or field types is daunting, 
though. 

This code tries to make it easy to make two kinds of in-process solr 
transforms:
* A filter, used in the analysis chain as you would, say, `solr.PatternReplaceFilterFactory`
* A variant on the normal `solr.StrField` type which allows you to do custom 
  transformations. This can be especially useful where `solr.TextField` 
  isn't great, like with ranges.
* A second variant on the `solr.StrField` type where the indexed value is 
  also the stored value (such that the stored value is _not_ whatever you 
  sent it, but whatever you munged that input to be).



## Building and using an analysis filter

Your schema.xml can use your custom filter anywhere in the analysis chain.

```xml
<!-- schema.xml or managed_schema -->

<fieldType name="smiley" class="solr.TextField">
  <analyzer>
    <tokenizer class="solr.ICUTokenizerFactory"/>
    <filter class="solr.TrimFilterFactory"/>
    <filter class="your.java.package.analysis.SmileyFilterFactory" echoInvalidInput="false"/>
  </analyzer>
</fieldType>

<field name="smileyText" type="smiley" mutiValued="true" indexe="true" 
       stored="true"/>

```

Just build a filter that extends SimpleFilter and implements `munge`, 
which takes an input string and returns the changed string.


```java
// Have solr_scaffold .jar file in your classpath

package your.java.package.analysis
import com.billdueber.solr_scaffold.analysis.SimpleFilter;
import org.apache.lucene.analysis.TokenStream;

/**
 * A subclass of SimpleFilter needs to have this two-argument
 * constructor that does nothing but call `super` and an implementation
 * of `munge` to take the input string and turn it into whatever you
 * actually want indexed.
 */

public class SmileyFilter extends SimpleFilter {

  public SmileyFilter(TokenStream aStream, Boolean echoInvalidInput) {
    super(aStream, echoInvalidInput);
  }

  @Override
  public String munge(String str) {
    return str.replaceAll("[Oo]", "😀");
  }
}
```

You also need a filter factory. It can look exactly as below, substituting 
the names of your custom filter.

```java
import com.billdueber.solr_scaffold.analysis.SimpleFilterFactory;
import org.apache.lucene.analysis.TokenStream;
import java.util.Map;

/**
 * You should be able to just search-and-replace SmileyFilter with whatever your
 * filter is called and leave everything else alone.
 */
public class SmileyFilterFactory extends SimpleFilterFactory {

  public SmileyFilterFactory(Map<String, String> args) {
    super(args);
  }

  public SmileyFilter create(TokenStream input) {
    return new SmileyFilter(input, getEchoInvalidInput());
  }
}
```

The `<filter>` tag in `schema.xml` takes a single argument, 
`echoInvalidInput`, which controls what happens if your `munge` method 
returns `null`.
* If `echoInvalidInput` is `true`, whatever was passed in will be used as 
  the index value.
* If `echoInvalidInput` is `false`, a `null` return from `munge` will 
  result in the value just disappearing. 

`echoInvalidInput` is most obviously useful when normalizing identifiers, 
where even invalid values might prove useful in a search. 

## Creating the custom String types

Creating the string types is even shorter; simply inherit from 
`MungedStringIndexedOnly` or `MungedStringIndexedAndStored` 
and provide an implementation of `munge`.

```java
package your.java.

package.schema;
import com.billdueber.solr_scaffold.schema.StringMungedIndexMungedStored;

public class SmileyString extends StringMungedIndexMungedStored {

  @Override
  public String munge(String str) {
    return str.replaceAll("[Oo]", "😀");
  }
}

```

Use it in your schema as follows:
```xml
<fieldType name="smiley_string" class="your.java.package.schema.
SmileyString" echoInvalidInput="false" />

```
## TODO

* Get this into maven central?
* Provide keyword-aware version of the filter
