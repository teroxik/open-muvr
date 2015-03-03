library(moments)

########################################################################################################################
#
# Data enrichment
#
########################################################################################################################

log = TRUE

# Writes a log message if logging is enabled.
#
# @param message    the message
writeLogMessage = function(message) {
  if (log) {
   write(message, stdout())
   flush(stdout())
  }
}

# String concatenation operator
"%+%" = paste0

# Forward composition operator
# x %|>% f = f(x)
#
# @param x  the value to apply to ``f``
# @param f  the function to evaluate with ``x``
"%|>%" = function (x, f) {
  f(x)
}

# Reads data frame from the provided csv file.
#
# @param input  path of the input file
readDataCsv = function(input) {
  writeLogMessage("Reading data from input file...")
  read.csv(file = input, header = TRUE)
}

# Enriches the data frame with function ``f``, called with
# ``windowSize`` rows.
#
# @param windowSize the size of the sliding window
# @param colName    the name of the column where the data will be stored
# @param f          the function that calculates the enriched values
# @param data       the input data
enrichData = function(windowSize, colName, f) {
  force(f)
  function(data) {
    writeLogMessage("Calculating " %+% colName %+% " ...")
    for (i in windowSize:nrow(data)) {
      data[i, colName] = f(data[(i - windowSize + 1):i,])
    }
    data
  }
}

# Saves data frame to csv file ``output``.
#
# @param output     the path of the output file
# @param data       the data frame to output
saveDataToCsv = function(output) {
  function(data) {
    writeLogMessage("Saving data to output file...")
    write.table(data, file = output, row.names = FALSE, sep = ",")
  }
}

# Calculates the signal vector magnitude of the
# samples. Calculation is done on a sample basis
# (ignores sliding window).
#
# @param data       the input data subset
signalVectorMagnitude = function(colX = "x", colY = "y", colZ = "z") {
  function(data) {
    sqrt( (data[[colX]] ^ 2) + (data[[colY]] ^ 2) + (data[[colZ]] ^ 2) )
  }
}

# Calculates a filtered data view using the SVM compared against a threshold value.
#
# @param column    the column name
# @param threshold (mG) threshold value: above and we keep the column data; below and we zero the column data
# @param SVMColumn name of the data column holding the SVM calculated result
signalVectorMagnitudeFilter = function(column, threshold = 2000, SVMColumn = "SVM") {
  function(data) {
    data[[column]]*(data[[SVMColumn]] >= threshold)
  }
}

# Calculates moving average by column.
#
# @param column     the column name
# @param data       the input data subset
movingAverageByColumn = function(column) {
  function(data) {
    mean(data[[column]])
  }
}

# Calculates moving average.
#
# @param data       the input data subset
movingAverage = function(colX = "x", colY = "y", colZ = "z") {
  function(data) {
    mean(c(data[[colX]], data[[colY]], data[[colZ]]))
  }
}

# Calculates simple standard deviation by column.
simpleStandardDeviationByColumn = function(column) {
  function(data) {
    sd(data[[column]])
  }
}

# Calculates simple standard deviation.
#
# @param data       the input data subset
simpleStandardDeviation = function(colX = "x", colY = "y", colZ = "z") {
  function(data) {
    sd(c(data[[colX]], data[[colY]], data[[colZ]]))
  }
}

# Calculates population standard deviation by column.
populationStandardDeviationByColumn = function(column) {
  function(data) {
    count = nrow(data)
    sd(data[[column]]) * sqrt((count - 1) / count)
  }
}

# Calculates population standard deviation.
#
# @param data       the input data subset
populationStandardDeviation = function(colX = "x", colY = "y", colZ = "z") {
  function(data) {
    count = nrow(data) * 3
    sd(c(data[[colX]], data[[colY]], data[[colZ]])) * sqrt((count - 1) / count)
  }
}

# Calculates interquartile range by column.
interquartileRangeByColumn = function(column, type = 1) {
  function(data) {
    IQR(data[[column]], type = type)
  }
}

# Calculates interquartile range.
interquartileRange = function(colX = "x", colY = "y", colZ = "z", type = 1) {
  function(data) {
    IQR(c(data[[colX]], data[[colY]], data[[colZ]]), type = type)
  }
}

# Calculates mean absolute deviation by column.
meanAbsoluteDeviationByColumn = function(column) {
  function(data) {
    dataMean = mean(data[[column]])
    mean(abs(data[[column]] - dataMean))
  }
}

# Calculates mean absolute deviation.
meanAbsoluteDeviation = function(colX = "x", colY = "y", colZ = "z") {
  function(data) {
    dataMean = mean(c(data[[colX]], data[[colY]], data[[colZ]]))
    mean(abs(c(data[[colX]] - dataMean, data[[colY]] - dataMean, data[[colY]] - dataMean)))
  }
}

# Calculates skewness by column.
skewnessByColumn = function(column) {
  function(data) {
    skewness(data[[column]])
  }
}

# Calculates skewness.
overallSkewness = function(colX = "x", colY = "y", colZ = "z") {
  function(data) {
    skewness(c(data[[colX]], data[[colY]], data[[colZ]]))
  }
}

# Calculates kurtosis by column.
kurtosisByColumn = function(column) {
  function(data) {
    kurtosis(data[[column]])
  }
}

# Calculates kurtosis.
overallKurtosis = function(colX = "x", colY = "y", colZ = "z") {
  function(data) {
    kurtosis(c(data[[colX]], data[[colY]], data[[colZ]]))
  }
}

# Calculates Q``quartile`` by column.
#
# @param quartile   the quartile numer (1, 2, 3)
quartileByColumn = function(quartile, column) {
  function(data) {
    quantile(data[[column]])[[quartile + 1]]
  }
}

# Calculates quartile.
#
# @param quartile   the quartile numer (1, 2, 3)
overallQuartile = function(quartile, colX = "x", colY = "y", colZ = "z") {
  function(data) {
    quantile(c(data[[colX]], data[[colY]], data[[colZ]]))[[quartile + 1]]
  }
}

# Enriches data with all implemented features.
#
# @param inputFile      path of the input csv file
# @param outputFile     path of the output csv file
# @param windowSize     the size of the sliding window
enrichDataWithAllFeatures = function(inputFile, outputFile, windowSize) {
  inputFile %|>%
  readDataCsv %|>%
  enrichData(1, "SVM", signalVectorMagnitude()) %|>%
  enrichData(windowSize, "MeanX", movingAverageByColumn("x")) %|>%
  enrichData(windowSize, "MeanY", movingAverageByColumn("y")) %|>%
  enrichData(windowSize, "MeanZ", movingAverageByColumn("z")) %|>%
  enrichData(windowSize, "Mean", movingAverage()) %|>%

  enrichData(windowSize, "ssdX", simpleStandardDeviationByColumn("x")) %|>%
  enrichData(windowSize, "ssdY", simpleStandardDeviationByColumn("y")) %|>%
  enrichData(windowSize, "ssdZ", simpleStandardDeviationByColumn("z")) %|>%
  enrichData(windowSize, "ssd", simpleStandardDeviation()) %|>%

  enrichData(windowSize, "psdX", populationStandardDeviationByColumn("x")) %|>%
  enrichData(windowSize, "psdY", populationStandardDeviationByColumn("y")) %|>%
  enrichData(windowSize, "psdZ", populationStandardDeviationByColumn("z")) %|>%
  enrichData(windowSize, "psd", populationStandardDeviation()) %|>%

  enrichData(windowSize, "iqrX", interquartileRangeByColumn("x")) %|>%
  enrichData(windowSize, "iqrY", interquartileRangeByColumn("y")) %|>%
  enrichData(windowSize, "iqrZ", interquartileRangeByColumn("z")) %|>%
  enrichData(windowSize, "iqr", interquartileRange()) %|>%

  enrichData(windowSize, "madX", meanAbsoluteDeviationByColumn("x")) %|>%
  enrichData(windowSize, "madY", meanAbsoluteDeviationByColumn("y")) %|>%
  enrichData(windowSize, "madZ", meanAbsoluteDeviationByColumn("z")) %|>%
  enrichData(windowSize, "mad", meanAbsoluteDeviation()) %|>%

  enrichData(windowSize, "skewX", skewnessByColumn("x")) %|>%
  enrichData(windowSize, "skewY", skewnessByColumn("y")) %|>%
  enrichData(windowSize, "skewZ", skewnessByColumn("z")) %|>%
  enrichData(windowSize, "skew", overallSkewness()) %|>%

  enrichData(windowSize, "kurtX", kurtosisByColumn("x")) %|>%
  enrichData(windowSize, "kurtY", kurtosisByColumn("y")) %|>%
  enrichData(windowSize, "kurtZ", kurtosisByColumn("z")) %|>%
  enrichData(windowSize, "kurt", overallKurtosis()) %|>%

  enrichData(windowSize, "q1X", quartileByColumn(1, "x")) %|>%
  enrichData(windowSize, "q1Y", quartileByColumn(1, "y")) %|>%
  enrichData(windowSize, "q1Z", quartileByColumn(1, "z")) %|>%
  enrichData(windowSize, "q1", overallQuartile(1)) %|>%

  enrichData(windowSize, "q2X", quartileByColumn(2, "x")) %|>%
  enrichData(windowSize, "q2Y", quartileByColumn(2, "y")) %|>%
  enrichData(windowSize, "q2Z", quartileByColumn(2, "z")) %|>%
  enrichData(windowSize, "q2", overallQuartile(2)) %|>%

  enrichData(windowSize, "q3X", quartileByColumn(3, "x")) %|>%
  enrichData(windowSize, "q3Y", quartileByColumn(3, "y")) %|>%
  enrichData(windowSize, "q3Z", quartileByColumn(3, "z")) %|>%
  enrichData(windowSize, "q3", overallQuartile(3)) %|>%

  # TODO: add more feature calculations here
  saveDataToCsv(outputFile)
  TRUE
}

enrichDataWithFeatures = function(data, windowSize) {
  data %|>%
  enrichData(1, "SVM", signalVectorMagnitude()) %|>%
  enrichData(windowSize, "MeanX", movingAverageByColumn("x")) %|>%
  enrichData(windowSize, "MeanY", movingAverageByColumn("y")) %|>%
  enrichData(windowSize, "MeanZ", movingAverageByColumn("z")) %|>%
  enrichData(windowSize, "Mean", movingAverage()) %|>%
  enrichData(windowSize, "ssdX", simpleStandardDeviationByColumn("x")) %|>%
  enrichData(windowSize, "ssdY", simpleStandardDeviationByColumn("y")) %|>%
  enrichData(windowSize, "ssdZ", simpleStandardDeviationByColumn("z")) %|>%
  enrichData(windowSize, "ssd", simpleStandardDeviation()) %|>%
  enrichData(windowSize, "psdX", populationStandardDeviationByColumn("x")) %|>%
  enrichData(windowSize, "psdY", populationStandardDeviationByColumn("y")) %|>%
  enrichData(windowSize, "psdZ", populationStandardDeviationByColumn("z")) %|>%
  enrichData(windowSize, "psd", populationStandardDeviation()) %|>%
  enrichData(windowSize, "iqrX", interquartileRangeByColumn("x")) %|>%
  enrichData(windowSize, "iqrY", interquartileRangeByColumn("y")) %|>%
  enrichData(windowSize, "iqrZ", interquartileRangeByColumn("z")) %|>%
  enrichData(windowSize, "iqr", interquartileRange()) %|>%
  enrichData(1, "x.filtered", signalVectorMagnitudeFilter("x")) %|>%
  enrichData(1, "y.filtered", signalVectorMagnitudeFilter("y")) %|>%
  enrichData(1, "z.filtered", signalVectorMagnitudeFilter("z"))
}
