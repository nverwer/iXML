<?xml version="1.0" ?>
<xsl:stylesheet version="2.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
  xmlns:xs="http://www.w3.org/2001/XMLSchema"
  xmlns:ftan="http://www.balisage.net/Proceedings/vol10/html/Kay01/BalisageVol10-Kay01.html"
  exclude-result-prefixes="xs ftan"
>

  <xsl:template match="@start | @end">
    <!-- omit -->
  </xsl:template>

  <xsl:template match="ftan:Number">
    <xsl:copy>
      <xsl:value-of select="data(.)"/>
    </xsl:copy>
  </xsl:template>

  <xsl:template match="ftan:Implicitnull">
    <ftan:Null/>
  </xsl:template>

  <xsl:template match="ftan:Escape">
    <xsl:choose>
      <xsl:when test="ftan:Space">
        <!-- Nothing; used for reformatting. -->
      </xsl:when>
      <xsl:when test="ftan:Hexcode">
        <xsl:value-of select="codepoints-to-string(ftan:hex-to-int(data(.)))"/>
      </xsl:when>
      <xsl:when test="ftan:Cell">
        <xsl:analyze-string select="data(.)" regex="^(.)(.*)\1$">
          <xsl:matching-substring>
            <xsl:value-of select="regex-group(2)"/>
          </xsl:matching-substring>
          <xsl:non-matching-substring>
            <xsl:comment>Bad cell-escape: <xsl:value-of select="concat('\[', data(.), ']')"/></xsl:comment>
          </xsl:non-matching-substring>
        </xsl:analyze-string>
      </xsl:when>
      <xsl:otherwise>
        <xsl:value-of select="concat('\', data(.))"/>
      </xsl:otherwise>
    </xsl:choose>
  </xsl:template>

  <xsl:template match="ftan:List[ftan:EmptyList] | ftan:Element[ftan:EmptyElement]">
    <xsl:copy>
      <!-- empty -->
    </xsl:copy>
  </xsl:template>

  <!-- Element that has an XML representation. -->

  <xsl:template match="
    ftan:Element[ ftan:Name/ftan:SimpleName and
                  not(ftan:Attribute[not(ftan:Name/ftan:SimpleName)
                                     or ftan:List or ftan:Element or ftan:RichText])
                ]">
    <xsl:element name="{data(ftan:Name)}">
      <xsl:apply-templates select="ftan:Attribute" mode="xml-element"/>
      <xsl:apply-templates select="ftan:Content" mode="xml-element"/>
    </xsl:element>
  </xsl:template>

  <xsl:template match="ftan:Attribute" mode="xml-element">
    <xsl:attribute name="{data(ftan:Name)}" select="data(*[not(self::ftan:Name)])"/>
  </xsl:template>

  <xsl:template match="ftan:Content | ftan:String | ftan:RichText" mode="xml-element">
    <xsl:apply-templates select="node()" mode="xml-element"/>
  </xsl:template>

  <xsl:template match="node()" mode="xml-element" priority="-1">
    <xsl:apply-templates select="."/>
  </xsl:template>

  <!-- Element that has no simple XML representation. -->

  <xsl:template match="ftan:Element">
    <xsl:copy>
      <xsl:attribute name="name" select="data(ftan:Name)"/>
      <xsl:apply-templates select="node()"/>
    </xsl:copy>
  </xsl:template>

  <xsl:template match="ftan:Attribute">
    <xsl:copy>
      <xsl:attribute name="name" select="data(ftan:Name)"/>
      <xsl:apply-templates select="node()"/>
    </xsl:copy>
  </xsl:template>

  <xsl:template match="ftan:Element/ftan:Name | ftan:Attribute/ftan:Name">
    <!-- omit -->
  </xsl:template>

  <xsl:template match="@*|node()" priority="-1">
    <xsl:copy>
      <xsl:apply-templates select="@*|node()"/>
    </xsl:copy>
  </xsl:template>


  <xsl:function name="ftan:hex-to-int" as="xs:integer">
    <xsl:param name="in"/> <!-- e.g. 030C -->
    <xsl:sequence select="
        if (string-length($in) eq 1) then
            ftan:hex-digit-to-integer($in)
        else
            16*ftan:hex-to-int(substring($in, 1, string-length($in)-1)) +
            ftan:hex-digit-to-integer(substring($in, string-length($in)))"/>
  </xsl:function>

  <xsl:function name="ftan:hex-digit-to-integer" as="xs:integer">
    <xsl:param name="char"/>
    <xsl:sequence select="string-length(substring-before('0123456789ABCDEF', upper-case($char)))"/>
  </xsl:function>

</xsl:stylesheet>
