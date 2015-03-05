library(moments)
library(entropy)
library(sets)

########################################################################################################################
#
# Data enrichment
#
########################################################################################################################

log = FALSE

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
  f(force(x))
}

# Reads data frame from the provided csv file.
#
# @param input  path of the input file
readDataCsv = function(input) {
  writeLogMessage("Reading data from \"" %+% input %+% "\" ...")
  read.csv(file = input, header = TRUE)
}

# Starts an enrichment batch by declaring windowSize.
#
# @param windowSize     defines the size of the window
# @param data           the data frame to process
enrichDataBatch = function(windowSize = 1) {
  function(data) {
    tuple(data = data, windowSize = windowSize, calculations = list())
  }
}

# Adds a calculation to the batch.
#
# @param colName        the name of the new column
# @param f              the function that calculates the values in hte column
# @param current        the current batch
addComputedColumn = function(colName, f) {
  force(f)
  function(current) {
    calculations = current[["calculations"]]
    calculations[[length(calculations) + 1]] = pair(colName = colName, f = f)
    tuple(data = current[["data"]], windowSize = current[["windowSize"]],
      calculations = calculations)
  }
}

# Runs the batch.
#
# @param colX       the name of the column X
# @param colY       the name of the column Y
# @param colZ       the name of the column Z
# @param final      the batch to run
runBatch = function(colX = "x", colY = "y", colZ = "z") {
  function(final) {
    data = final[["data"]]
    windowSize = final[["windowSize"]]
    calculations = final[["calculations"]]
    count = nrow(data)
    m = matrix(c(data[[colX]], data[[colY]], data[[colZ]]), ncol = 3)
    for (i in windowSize:count) {
      currentWindow = m[(i - windowSize + 1):i,]
      for (calc in calculations) {
        if (!is.null(calc)) {
          colName = calc[["colName"]]
          f = calc[["f"]]
          data[i, colName] = f(currentWindow)
        }
      }
    }
    data
  }
}

# Enriches the data frame with function ``f``, called with
# ``windowSize`` rows.
#
# @param windowSize the size of the sliding window
# @param colName    the name of the column where the data will be stored
# @param f          the function that calculates the enriched values
# @param data       the input data
enrichData = function(windowSize, colName, f, colX = "x", colY = "y", colZ = "z") {
  force(f)
  function(data) {
    count = nrow(data)
    m = matrix(c(data[[colX]], data[[colY]], data[[colZ]]), ncol = 3)
    for (i in windowSize:count) {
      data[i, colName] = f(m[(i - windowSize + 1):i,])
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
    writeLogMessage("Saving data to \"" %+% output %+% "\" ...")
    write.table(data, file = output, row.names = FALSE, sep = ",")
  }
}

# Calculates the signal vector magnitude of the
# samples. Calculation is done on a sample basis
# (ignores sliding window).
#
# @param data       the input data subset
signalVectorMagnitude = function(data) {
  sqrt( (as.double(data[1]) ^ 2) + (as.double(data[2]) ^ 2) + (as.double(data[3]) ^ 2) )
}

# Calculates a filtered data view using the SVM compared against a threshold value.
#
# @param column    the column name
# @param threshold (mG) threshold value: above and we keep the column data; below and we zero the column data
# @param SVMColumn name of the data column holding the SVM calculated result
signalVectorMagnitudeFilter = function(column, threshold = 2000) {
  function(data) {
    svm = sqrt( (as.double(data[1]) ^ 2) + (as.double(data[2]) ^ 2) + (as.double(data[3]) ^ 2) )
    as.double(data[column])*(svm >= threshold)
  }
}

# Calculates moving average by column.
#
# @param column     the column name
# @param data       the input data subset
movingAverageByColumn = function(column) {
  function(data) {
    mean(as.double(data[, column]))
  }
}

# Calculates moving average.
#
# @param data       the input data subset
movingAverage = function(data) {
  mean(c(as.double(data[, 1]), as.double(data[, 2]), as.double(data[, 3])))
}

# Calculates simple standard deviation by column.
simpleStandardDeviationByColumn = function(column) {
  function(data) {
    sd(as.double(data[, column]))
  }
}

# Calculates simple standard deviation.
#
# @param data       the input data subset
simpleStandardDeviation = function(data) {
  sd(c(as.double(data[, 1]), as.double(data[, 2]), as.double(data[, 3])))
}

# Calculates population standard deviation by column.
populationStandardDeviationByColumn = function(column) {
  function(data) {
    count = nrow(data)
    sd(as.double(data[, column])) * sqrt((count - 1) / count)
  }
}

# Calculates population standard deviation.
#
# @param data       the input data subset
populationStandardDeviation = function(data) {
  count = nrow(data) * 3
  sd(c(as.double(data[, 1]), as.double(data[, 2]), as.double(data[, 3]))) * sqrt((count - 1) / count)
}

# Calculates interquartile range by column.
interquartileRangeByColumn = function(column, type = 1) {
  function(data) {
    IQR(as.double(data[, column]), type = type, na.rm = TRUE)
  }
}

# Calculates interquartile range.
interquartileRange = function(type = 1) {
  function(data) {
    IQR(c(as.double(data[, 1]), as.double(data[, 2]), as.double(data[, 3])), type = type, na.rm = TRUE)
  }
}

# Calculates mean absolute deviation by column.
meanAbsoluteDeviationByColumn = function(column) {
  function(data) {
    dataMean = mean(as.double(data[, column]))
    mean(abs(as.double(data[, column]) - dataMean))
  }
}

# Calculates mean absolute deviation.
meanAbsoluteDeviation = function(data) {
  dataMean = mean(c(as.double(data[, 1]), as.double(data[, 2]), as.double(data[, 3])))
  mean(abs(c(as.double(data[, 1]) - dataMean, as.double(data[, 2]) - dataMean, as.double(data[, 3]) - dataMean)))
}

# Calculates skewness by column.
skewnessByColumn = function(column) {
  function(data) {
    skewness(as.double(data[, column]))
  }
}

# Calculates skewness.
overallSkewness = function(data) {
  skewness(c(as.double(data[, 1]), as.double(data[, 2]), as.double(data[, 3])))
}

# Calculates kurtosis by column.
kurtosisByColumn = function(column) {
  function(data) {
    kurtosis(as.double(data[, column]))
  }
}

# Calculates kurtosis.
overallKurtosis = function(data) {
  kurtosis(c(as.double(data[, 1]), as.double(data[, 2]), as.double(data[, 3])))
}

# Calculates Q``quartile`` by column.
#
# @param quartile   the quartile numer (1, 2, 3)
quartileByColumn = function(quartile, column) {
  function(data) {
    quantile(as.double(data[, column]), na.rm = TRUE)[[quartile + 1]]
  }
}

# Calculates quartile.
#
# @param quartile   the quartile numer (1, 2, 3)
overallQuartile = function(quartile) {
  function(data) {
    quantile(c(as.double(data[, 1]), as.double(data[, 2]), as.double(data[, 3])), na.rm = TRUE)[[quartile + 1]]
  }
}

# Calculates signal vector area by column.
signalVectorAreaByColumn = function(column) {
  function(data) {
    sum(abs(as.double(data[, column])))
  }
}

# Calculates signal vector area.
signalVectorArea = function(data) {
  sum(abs(c(as.double(data[, 1]), as.double(data[, 2]), as.double(data[, 3]))))
}

# Calculates empirical entropy by column.
entropyByColumn = function(column) {
  function(data) {
    entropy.empirical(as.double(data[, column]))
  }
}

# Calculates empirical entropy.
overallEntropy = function(data) {
  entropy.empirical(c(as.double(data[, 1]), as.double(data[, 2]), as.double(data[, 3])))
}

# Enriches data with all implemented features.
#
# @param inputFile      path of the input csv file
# @param outputFile     path of the output csv file
# @param windowSize     the size of the sliding window
enrichDataWithAllFeatures = function(inputFile, outputFile, windowSize) {
  inputFile %|>%
  readDataCsv %|>%
  enrichData(1, "SVM", signalVectorMagnitude) %|>%

  enrichData(windowSize, "MeanX", movingAverageByColumn(1)) %|>%
  enrichData(windowSize, "MeanY", movingAverageByColumn(2)) %|>%
  enrichData(windowSize, "MeanZ", movingAverageByColumn(3)) %|>%
  enrichData(windowSize, "Mean", movingAverage) %|>%

  enrichData(windowSize, "ssdX", simpleStandardDeviationByColumn(1)) %|>%
  enrichData(windowSize, "ssdY", simpleStandardDeviationByColumn(2)) %|>%
  enrichData(windowSize, "ssdZ", simpleStandardDeviationByColumn(3)) %|>%
  enrichData(windowSize, "ssd", simpleStandardDeviation) %|>%

  enrichData(windowSize, "psdX", populationStandardDeviationByColumn(1)) %|>%
  enrichData(windowSize, "psdY", populationStandardDeviationByColumn(2)) %|>%
  enrichData(windowSize, "psdZ", populationStandardDeviationByColumn(3)) %|>%
  enrichData(windowSize, "psd", populationStandardDeviation) %|>%

  enrichData(windowSize, "iqrX", interquartileRangeByColumn(1)) %|>%
  enrichData(windowSize, "iqrY", interquartileRangeByColumn(2)) %|>%
  enrichData(windowSize, "iqrZ", interquartileRangeByColumn(3)) %|>%
  enrichData(windowSize, "iqr", interquartileRange()) %|>%

  enrichData(windowSize, "madX", meanAbsoluteDeviationByColumn(1)) %|>%
  enrichData(windowSize, "madY", meanAbsoluteDeviationByColumn(2)) %|>%
  enrichData(windowSize, "madZ", meanAbsoluteDeviationByColumn(3)) %|>%
  enrichData(windowSize, "mad", meanAbsoluteDeviation) %|>%

  enrichData(windowSize, "skewX", skewnessByColumn(1)) %|>%
  enrichData(windowSize, "skewY", skewnessByColumn(2)) %|>%
  enrichData(windowSize, "skewZ", skewnessByColumn(3)) %|>%
  enrichData(windowSize, "skew", overallSkewness) %|>%

  enrichData(windowSize, "kurtX", kurtosisByColumn(1)) %|>%
  enrichData(windowSize, "kurtY", kurtosisByColumn(2)) %|>%
  enrichData(windowSize, "kurtZ", kurtosisByColumn(3)) %|>%
  enrichData(windowSize, "kurt", overallKurtosis) %|>%

  enrichData(windowSize, "q1X", quartileByColumn(1, 1)) %|>%
  enrichData(windowSize, "q1Y", quartileByColumn(1, 2)) %|>%
  enrichData(windowSize, "q1Z", quartileByColumn(1, 3)) %|>%
  enrichData(windowSize, "q1", overallQuartile(1)) %|>%

  enrichData(windowSize, "q2X", quartileByColumn(2, 1)) %|>%
  enrichData(windowSize, "q2Y", quartileByColumn(2, 2)) %|>%
  enrichData(windowSize, "q2Z", quartileByColumn(2, 3)) %|>%
  enrichData(windowSize, "q2", overallQuartile(2)) %|>%

  enrichData(windowSize, "q3X", quartileByColumn(3, 1)) %|>%
  enrichData(windowSize, "q3Y", quartileByColumn(3, 2)) %|>%
  enrichData(windowSize, "q3Z", quartileByColumn(3, 3)) %|>%
  enrichData(windowSize, "q3", overallQuartile(3)) %|>%

  enrichData(windowSize, "svaX", signalVectorAreaByColumn(1)) %|>%
  enrichData(windowSize, "svaY", signalVectorAreaByColumn(2)) %|>%
  enrichData(windowSize, "svaZ", signalVectorAreaByColumn(3)) %|>%
  enrichData(windowSize, "sva", signalVectorArea) %|>%

  enrichData(windowSize, "entX", entropyByColumn(1)) %|>%
  enrichData(windowSize, "entY", entropyByColumn(2)) %|>%
  enrichData(windowSize, "entZ", entropyByColumn(3)) %|>%
  enrichData(windowSize, "ent", overallEntropy) %|>%

  # TODO: add more feature calculations here
  saveDataToCsv(outputFile)
  TRUE
}
