<?xml version="1.0" encoding="UTF-8"?>

<xsl:stylesheet version="1.0"
xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
xmlns:d="http://nlp.stanford.edu/CoreNLP/v1">

<xsl:output method="html"/>

<xsl:template match="/">
  <html>
  <body>
    <center><h2>Stanford CoreNLP XML Output</h2></center>
    <hr size="3" color="#333333"/>
    <center><h3>Document</h3></center>
    <table border="1" style="background-color:#f0f0f0;" align="center">
    <tr><th>Sentences</th></tr>
    <xsl:for-each select="root/document/sentences/sentence">
      <tr><td>
      <xsl:apply-templates select=".">
        <xsl:with-param name="position" select="position()"/>
      </xsl:apply-templates>
      </td></tr>
    </xsl:for-each>
    
    <tr><th>Coreference resolution graph</th></tr>
    <tr><td>
    <xsl:apply-templates select="root/document/coreference"/>
    </td></tr>
    </table>

  </body>
  </html>
</xsl:template>

<xsl:template match="root/document/sentences/sentence">
  <xsl:param name="position" select="'0'"/>
  <i><b>Sentence #<xsl:value-of select="$position"/></b></i>

  <p>
  <i>Tokens</i><br/>
  <xsl:apply-templates select="tokens"/>
  </p>

  <p>
  <i>Parse tree</i><br/>
  <xsl:value-of select="parse"/>
  </p>

  <p>
  <i>Uncollapsed dependencies</i>
  <ul>
  <xsl:for-each select="basic-dependencies">
    <xsl:apply-templates select="dep"/>
  </xsl:for-each>
  </ul>
  </p>

  <p>
  <i>Collapsed dependencies</i>
  <ul>
  <xsl:for-each select="collapsed-dependencies">
    <xsl:apply-templates select="dep"/>
  </xsl:for-each>
  </ul>
  </p>

  <p>
  <i>Collapsed dependencies with CC processed</i>
  <ul>
  <xsl:for-each select="collapsed-ccprocessed-dependencies">
    <xsl:apply-templates select="dep"/>
  </xsl:for-each>
  </ul>
  </p>
</xsl:template>

<xsl:template match="tokens">
  <table border="1">
  <tr>
    <th>Id</th>
    <th>Word</th>
    <th>Lemma</th>
    <th>Char begin</th>
    <th>Char end</th>
    <th>POS</th>
    <th>NER</th>
    <th>Normalized NER</th>
  </tr>
  <xsl:for-each select="token">
    <tr>
      <td><xsl:value-of select="@id"/></td>
      <td><xsl:value-of select="word"/></td>
      <td><xsl:value-of select="lemma"/></td>
      <td><xsl:value-of select="CharacterOffsetBegin"/></td>
      <td><xsl:value-of select="CharacterOffsetEnd"/></td>
      <td><xsl:value-of select="POS"/></td>
      <td><xsl:value-of select="NER"/></td>
      <td><xsl:value-of select="NormalizedNER"/></td>
    </tr>
  </xsl:for-each>
  </table>
</xsl:template>

<xsl:template match="basic-dependencies">
  <ul>
  <xsl:for-each select="dep">
    <xsl:apply-templates select="."/>
  </xsl:for-each>
  </ul>
</xsl:template>

<xsl:template match="collapsed-dependencies">
  <ul>
  <xsl:for-each select="dep">
    <xsl:apply-templates select="."/>
  </xsl:for-each>
  </ul>
</xsl:template>

<xsl:template match="collapsed-ccprocessed-dependencies">
  <ul>
  <xsl:for-each select="dep">
    <xsl:apply-templates select="."/>
  </xsl:for-each>
  </ul>
</xsl:template>

<xsl:template match="dep">
    <li>
      <xsl:value-of select="@type"/>
      (
      <xsl:value-of select="governor"/>-<xsl:value-of select="governor/@idx"/>
      ,
      <xsl:value-of select="dependent"/>-<xsl:value-of select="dependent/@idx"/>
      )
    </li>
</xsl:template>

<xsl:template match="coreference">
  <ol>
  <xsl:for-each select="coreference">
    <li>
    <ul>
    <xsl:for-each select="mention">
      <li> sentence <xsl:value-of select="sentence"/>,
           headword <xsl:value-of select="head"/> 
           <xsl:if test="@representative"> (gov) </xsl:if>
      </li>
    </xsl:for-each>
    </ul>
    </li>
  </xsl:for-each>
  </ol>
</xsl:template>

</xsl:stylesheet>
