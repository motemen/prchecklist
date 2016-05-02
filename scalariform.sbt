import scalariform.formatter.preferences._

ScalariformKeys.preferences := ScalariformKeys.preferences.value
  .setPreference(PreserveDanglingCloseParenthesis, true)
  .setPreference(PreserveSpaceBeforeArguments, true)
  .setPreference(DoubleIndentClassDeclaration, true)
  .setPreference(AlignSingleLineCaseStatements, true)
