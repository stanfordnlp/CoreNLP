@echo off

:: Usage: "segment ctb|pk filename encoding kBest"
:: encoding can be UTF-8 or GB18030 or GB

if "%4"=="" (
  echo Too few arguments
  call :usage %~nx0
  goto :EOF
  )
if not "%6"=="" (
  echo Too many arguments
  call :usage %~nx0
  goto :EOF
  )

set ARGS=-keepAllWhitespaces false
if not "%5"=="" (
  if not "%1"=="-k" (
    echo First argument must be "-k"
    call :usage %~nx0
    goto :EOF
    )
  set ARGS=-keepAllWhitespaces true
  set lang=%~2
  set file=%~3
  set enc=%~4
  set kBest=%~5
) else (
  if not "%4"=="" (
    set lang=%~1
    set file=%~2
    set enc=%~3
    set kBest=%~4
    ) else (
      echo Unknown argument error
      call :usage %~nx0
      goto :EOF
    )
  )

if "%lang%"=="ctb" (
  echo CTB: Chinese Treebank segmentation >&2
) else (
  if "%lang%"=="pku" (
    echo PKU: Beijing University segmentation >&2
  ) else (
    echo Language argument should be either ctb or pku. Abort
    goto :EOF
    )
  )

echo File: "%file%" >&2
echo Encoding: "%enc%" >&2
echo kBest: "%kBest%" >&2
echo ------------------------------- >&2

set BASEDIR=%~dp0
set DATADIR=%BASEDIR%data
:: set LEXDIR=%DATADIR%lexicons
set JAVACMD=java -mx1024m -cp "%BASEDIR%*;" edu.stanford.nlp.ie.crf.CRFClassifier -sighanCorporaDict "%DATADIR%" -textFile "%file%" -inputEncoding %enc% -sighanPostProcessing true %ARGS%
set DICTS=%DATADIR%\dict-chris6.ser.gz
set KBESTCMD=
if not %kBest%==0 set kBestCmd=-kBest %kBest%

if "%lang%"=="ctb" (
  %JAVACMD% -loadClassifier "%DATADIR%\%lang%.gz" -serDictionary "%DICTS%" "%KBESTCMD%"
  )
if "%lang%"=="pku" (
  %JAVACMD% -loadClassifier "%DATADIR%\%lang%.gz" -serDictionary "%DICTS%" "%KBESTCMD%"
  )

goto :EOF

:usage
  echo Usage: "%1 [-k] ctb|pku filename encoding kBest" >&2
  echo   -k   : keep whitespaces >&2
  echo   ctb  : use Chinese Treebank segmentation >&2
  echo   pku  : Beijing University segmentation >&2
  echo   kBest: print kBest best segmenations; 0 means kBest mode is off. >&2
  echo. >&2
  echo Example: %1 ctb test.simp.utf8 UTF-8 0 >&2
  echo Example: %1 pku test.simp.utf8 UTF-8 0 >&2
  goto :EOF
