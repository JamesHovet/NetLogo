// (C) Uri Wilensky. https://github.com/NetLogo/NetLogo

package org.nlogo.lex

import org.nlogo.api, api.{ Token, TokenType }

class Tokenizer extends api.TokenizerInterface {

  // this method never throws CompilerException, but use TokenType.BAD
  // instead, because if there's an error we want to still
  // keep going, so that findProcedurePositions and AutoConverter won't
  // be useless even if there's a tokenization error in the code
  // - ST 4/21/03, 5/24/03, 6/29/06
  def tokenizeRobustly(source: String): Seq[Token] =
    doTokenize(source, false, "", false)

  // and here's one that throws CompilerException as soon as it hits a bad token - ST 2/20/08
  def tokenize(source: String, fileName: String): Seq[Token] = {
    val result = doTokenize(source, false, fileName, true)
    result.find(_.tpe == TokenType.Bad) match {
      case Some(badToken) =>
        throw new api.CompilerException(badToken)
      case None =>
        result
    }
  }

  // this is used e.g. when colorizing
  private def tokenizeIncludingComments(source: String): Seq[Token] =
    doTokenize(source, true, "", false)

  // includeCommentTokens is used for syntax highlighting in the editor - ST 7/7/06
  private def doTokenize(source: String, includeCommentTokens: Boolean,
                         fileName: String, stopAtFirstBadToken: Boolean): Seq[Token] =
  {
    val yy = new TokenLexer(
      new java.io.StringReader(source),
      fileName)
    val eof = new Token("", TokenType.EOF, "")(0, 0, "")
    def yystream: Stream[Token] = {
      val t = yy.yylex()
      if (t == null)
        Stream(eof)
      else if (stopAtFirstBadToken && t.tpe == TokenType.Bad)
        Stream(t, eof)
      else
        Stream.cons(t, yystream)
    }
    yystream
      .filter(includeCommentTokens || _.tpe != TokenType.Comment)
      .map(handleSpecialIdentifiers)
      .toList
  }

  // this could be part of IdentifierParser, even. handling it here for
  // now, pending a total rewrite of IdentifierParser - ST 5/6/13
  private def handleSpecialIdentifiers(t: Token): Token =
    if (Keywords.isKeyword(t.name))
      t.copy(tpe = TokenType.Keyword)
    else if (Variables.isVariable(t.name))
      t.copy(tpe = TokenType.Variable)
    else Constants.get(t.name) match {
      case Some(value) =>
        t.copy(tpe = TokenType.Literal, value = value)
      case None =>
        t
    }

  def nextToken(reader: java.io.BufferedReader): Token =
    handleSpecialIdentifiers(
      new TokenLexer(reader, null).yylex())

  def getTokenAtPosition(source: String, position: Int): Token = {
    // if the cursor is between two adjacent tokens we'll need to pick the token
    // the user probably wants for F1 purposes. see bug #139 - ST 5/2/12
    val interestingTokenTypes =
      List(TokenType.Literal, TokenType.Ident, TokenType.Command, TokenType.Reporter,
           TokenType.Keyword, TokenType.Variable)
    val candidates =
      tokenizeIncludingComments(source)
        .dropWhile(_.endPos < position)
        .takeWhile(_.startPos <= position)
        .take(2) // be robust against EOF tokens, etc.
    candidates match {
      case Seq() => null
      case Seq(t) => t
      case Seq(t1, t2) =>
        if (interestingTokenTypes.contains(t2.tpe))
          t2 else t1
    }
  }

  def isValidIdentifier(ident: String): Boolean =
    tokenizeRobustly(ident).take(2).map(_.tpe) ==
      Seq(TokenType.Ident, TokenType.EOF)

  def tokenizeForColorization(source: String, extensionManager: api.ExtensionManager): Seq[Token] = {
    // In order for extension primitives to be the right color, we need to change
    // the type of the token from TokenType.Ident to TokenType.Command or TokenType.Reporter
    // if the identifier is recognized by the extension.
    def replaceImports(token: Token): Token =
      if (!extensionManager.anyExtensionsLoaded || token.tpe != TokenType.Ident)
        token
      // look up the replacement.
      else extensionManager.replaceIdentifier(token.value.asInstanceOf[String]) match {
        case null => token
        case prim =>
          val newType =
            if (prim.isInstanceOf[api.Command])
              TokenType.Command
            else TokenType.Reporter
          new Token(token.name, newType, token.value)(
            token.startPos, token.endPos, token.fileName)
      }
    tokenizeIncludingComments(source)
      .takeWhile(_.tpe != TokenType.EOF)
      .map(replaceImports)
  }

}
