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
    <tr><th>Document Info</th></tr>
    <tr><td>
    <xsl:if test="root/document/docId">
        <br/><i>DocId</i>: <xsl:value-of select="root/document/docId"/>
    </xsl:if>
    <xsl:if test="root/document/docDate">
        <br/><i>DocDate</i>: <xsl:value-of select="root/document/docDate"/>
    </xsl:if>
    <xsl:if test="root/document/docSourceType">
        <br/><i>DocSourceType</i>: <xsl:value-of select="root/document/docSourceType"/>
    </xsl:if>
    <xsl:if test="root/document/docType">
        <br/><i>DocType</i>: <xsl:value-of select="root/document/docType"/>
    </xsl:if>
    <xsl:if test="root/document/author">
        <br/><i>Author</i>: <xsl:value-of select="root/document/author"/>
    </xsl:if>
    <xsl:if test="root/document/location">
        <br/><i>Location</i>: <xsl:value-of select="root/document/location"/>
    </xsl:if>
    </td></tr>

    <xsl:if test="root/document/text">
        <tr><th>Text</th></tr>
        <tr><td>
            <div class="preformatted">
              <xsl:value-of select="root/document/text"/>
            </div>
        </td></tr>
    </xsl:if>

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
  <p><i><b>Sentence #<xsl:value-of select="$position"/></b></i>
  <xsl:if test="@sentiment">
        <xsl:text> Sentiment: </xsl:text><xsl:value-of select="@sentiment"/>
  </xsl:if>
  </p>

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
  <xsl:for-each select="dependencies[@type='basic-dependencies']">
    <xsl:apply-templates select="dep"/>
  </xsl:for-each>
  </ul>
  </p>

  <p>
  <i>Enhanced dependencies</i>
  <ul>
  <xsl:for-each select="dependencies[@type='collapsed-ccprocessed-dependencies']">
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
    <th>Speaker</th>
    <th>Sentiment</th>
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
      <td><xsl:value-of select="Speaker"/></td>
      <td><xsl:value-of select="sentiment"/></td>
    </tr>
  </xsl:for-each>
  </table>
</xsl:template>

<xsl:template match="dependencies">
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
      <xsl:value-of select="governor"/><xsl:if test="governor/@copy">^<xsl:value-of select="governor/@copy"/></xsl:if>-<xsl:value-of select="governor/@idx"/>
      ,
      <xsl:value-of select="dependent"/><xsl:if test="dependent/@copy">^<xsl:value-of select="dependent/@copy"/></xsl:if>-<xsl:value-of select="dependent/@idx"/>
      ) 
      <xsl:if test="@extra">(extra)</xsl:if>
    </li>
</xsl:template>

<xsl:template match="coreference">
  <ol>
  <xsl:for-each select="coreference">
    <li>
        <table border="1">
        <tr>
            <th>Sentence</th>
            <th>Head</th>
            <th>Text</th>
            <th>Context</th>
        </tr>
        <xsl:for-each select="mention">
            <tr>
                <td><xsl:value-of select="sentence"/></td>
                <td><xsl:value-of select="head"/> <xsl:if test="@representative"> (gov) </xsl:if></td>
                <td><xsl:value-of select="text"/></td>
                <td><xsl:if test="leftContext or rightContext">
                    ...<xsl:value-of select="leftContext"/>
                    <span style="background-color: #99ff99;">
                      <xsl:text> </xsl:text>
                      <xsl:value-of select="text"/></span>
                      <xsl:text> </xsl:text>
                    <xsl:value-of select="rightContext"/>...
                    </xsl:if>
                </td>
            </tr>
        </xsl:for-each>
        </table>
    </li>
  </xsl:for-each>
  </ol>
</xsl:template>

</xsl:stylesheet>
