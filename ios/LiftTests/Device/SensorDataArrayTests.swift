import Foundation
import XCTest

class SensorDataArrayTests : XCTestCase {
    
    let dash: UInt8 = 0x2d
    
    func testContinuousRanges() {
        var sda = SensorDataArray(header: SensorDataArrayHeader(sourceDeviceId: DeviceId(), type: 0, sampleSize: 1, samplesPerSecond: 1))
        sda.append(sensorData: SensorData.fromString("abcdefghij", startingAt: 0), maximumGap: 0, gapValue: dash)       // 0 - 10
        sda.append(sensorData: SensorData.fromString("0123456789", startingAt: 11), maximumGap: 0, gapValue: dash)      // 11 - 21
        sda.append(sensorData: SensorData.fromString("ABCDEFGHIJ", startingAt: 22), maximumGap: 0, gapValue: dash)      // 22 - 32
        
        let cr = sda.continuousRanges(maximumGap: 1)
        XCTAssertEqual(cr.count, 1)
        XCTAssertEqual(cr[0].start, 0); XCTAssertEqual(cr[0].end, 32)

        let crC = sda.continuousRanges(maximumGap: 100)
        XCTAssertEqual(crC.count, 1)
        XCTAssertEqual(crC[0].start, 0); XCTAssertEqual(crC[0].end, 32)

        let dr = sda.continuousRanges(maximumGap: 0)
        XCTAssertEqual(dr.count, 3)
        XCTAssertEqual(dr[0].start, 0); XCTAssertEqual(dr[0].end, 10)
        XCTAssertEqual(dr[1].start, 11); XCTAssertEqual(dr[1].end, 21)
        XCTAssertEqual(dr[2].start, 22); XCTAssertEqual(dr[2].end, 32)
    }
    
    func testSamplesSlicing() {
        var sda = SensorDataArray(header: SensorDataArrayHeader(sourceDeviceId: DeviceId(), type: 0, sampleSize: 10, samplesPerSecond: 1))
        sda.append(sensorData: SensorData.fromString("abcdefghij", startingAt: 0), maximumGap: 0, gapValue: dash)       // 0 - 10
        sda.append(sensorData: SensorData.fromString("0123456789", startingAt: 1), maximumGap: 0, gapValue: dash)      // 11 - 21
        sda.append(sensorData: SensorData.fromString("ABCDEFGHIJ", startingAt: 2), maximumGap: 0, gapValue: dash)      // 22 - 32
        
        var slice = sda.slice(TimeRange(start: 0, end: 2), maximumGap: 1, gapValue: dash)
        assertSliceString(slice, expectedContent: "abcdefghij0123456789", start: 0, end: 2)
        
        slice = sda.slice(TimeRange(start: 2, end: 3), maximumGap: 1, gapValue: dash)
        assertSliceString(slice, expectedContent: "ABCDEFGHIJ", start: 0, end: 2)
        
        slice = sda.slice(TimeRange(start: 0, end: 1.6), maximumGap: 1, gapValue: dash)
        assertSliceString(slice, expectedContent: "abcdefghij0123456789", start: 0, end: 1.6)
        
        slice = sda.slice(TimeRange(start: 1.6, end: 3), maximumGap: 1, gapValue: dash)
        assertSliceString(slice, expectedContent: "ABCDEFGHIJ", start: 1.6, end: 3)
        
        slice = sda.slice(TimeRange(start: 0, end: 1.5), maximumGap: 1, gapValue: dash)
        assertSliceString(slice, expectedContent: "abcdefghij0123456789", start: 0, end: 1.5)
        
        slice = sda.slice(TimeRange(start: 1.5, end: 3), maximumGap: 1, gapValue: dash)
        assertSliceString(slice, expectedContent: "ABCDEFGHIJ", start: 1.5, end: 3)
        
        slice = sda.slice(TimeRange(start: 0, end: 1.4), maximumGap: 1, gapValue: dash)
        assertSliceString(slice, expectedContent: "abcdefghij", start: 0, end: 1.4)
        
        slice = sda.slice(TimeRange(start: 1.4, end: 3), maximumGap: 1, gapValue: dash)
        assertSliceString(slice, expectedContent: "0123456789ABCDEFGHIJ", start: 1.4, end: 3)
    }
    
    func testSamplesSlicingWithBigGaps() {
        var sda = SensorDataArray(header: SensorDataArrayHeader(sourceDeviceId: DeviceId(), type: 0, sampleSize: 10, samplesPerSecond: 1))
        sda.append(sensorData: SensorData.fromString("abcdefghij", startingAt: 0), maximumGap: 0, gapValue: dash)       // 0 - 10
        sda.append(sensorData: SensorData.fromString("0123456789", startingAt: 1.5), maximumGap: 0, gapValue: dash)    // 15 - 25
        sda.append(sensorData: SensorData.fromString("ABCDEFGHIJ", startingAt: 3), maximumGap: 0, gapValue: dash)      // 30 - 40
        
        let gap = "----------"
        
        var slice = sda.slice(TimeRange(start: 0, end: 2), maximumGap: 1, gapValue: dash)
        assertSliceString(slice, expectedContent: "abcdefghij" + gap + "0123456789", start: 0, end: 2)
        
        slice = sda.slice(TimeRange(start: 2, end: 4), maximumGap: 1, gapValue: dash)
        assertSliceString(slice, expectedContent: gap + "ABCDEFGHIJ", start: 2, end: 4)
        
        slice = sda.slice(TimeRange(start: 0, end: 1.9), maximumGap: 1, gapValue: dash)
        assertSliceString(slice, expectedContent: "abcdefghij" + gap, start: 0, end: 1.9)
        
        slice = sda.slice(TimeRange(start: 1.9, end: 4), maximumGap: 1, gapValue: dash)
        assertSliceString(slice, expectedContent: "0123456789" + gap + "ABCDEFGHIJ", start: 1.9, end: 4)
        
        slice = sda.slice(TimeRange(start: 0, end: 3), maximumGap: 1, gapValue: dash)
        assertSliceString(slice, expectedContent: "abcdefghij" + gap + "0123456789" + gap, start: 0, end: 3)
        
        slice = sda.slice(TimeRange(start: 3, end: 4), maximumGap: 1, gapValue: dash)
        assertSliceString(slice, expectedContent: "ABCDEFGHIJ", start: 3, end: 4)
    }
    
    func testSamplesSlicingWithSmallGaps() {
        var sda = SensorDataArray(header: SensorDataArrayHeader(sourceDeviceId: DeviceId(), type: 0, sampleSize: 10, samplesPerSecond: 1))
        sda.append(sensorData: SensorData.fromString("abcdefghij", startingAt: 0), maximumGap: 0, gapValue: dash)       // 0 - 10
        sda.append(sensorData: SensorData.fromString("0123456789", startingAt: 1.2), maximumGap: 0, gapValue: dash)    // 12 - 22
        sda.append(sensorData: SensorData.fromString("ABCDEFGHIJ", startingAt: 2.4), maximumGap: 0, gapValue: dash)      // 24 - 34
        
        let gap = "----------"
        
        var slice = sda.slice(TimeRange(start: 0, end: 2), maximumGap: 1, gapValue: dash)
        assertSliceString(slice, expectedContent: "abcdefghij0123456789", start: 0, end: 2)
        
        slice = sda.slice(TimeRange(start: 2, end: 4), maximumGap: 1, gapValue: dash)
        assertSliceString(slice, expectedContent: "ABCDEFGHIJ" + gap, start: 2, end: 4)
        
        slice = sda.slice(TimeRange(start: 0, end: 1.9), maximumGap: 1, gapValue: dash)
        assertSliceString(slice, expectedContent: "abcdefghij0123456789", start: 0, end: 1.9)
        
        slice = sda.slice(TimeRange(start: 1.9, end: 4), maximumGap: 1, gapValue: dash)
        assertSliceString(slice, expectedContent: "ABCDEFGHIJ" + gap, start: 1.9, end: 4)
        
        slice = sda.slice(TimeRange(start: 0, end: 1.2), maximumGap: 1, gapValue: dash)
        assertSliceString(slice, expectedContent: "abcdefghij", start: 0, end: 1.2)
        
        slice = sda.slice(TimeRange(start: 1.2, end: 4), maximumGap: 1, gapValue: dash)
        assertSliceString(slice, expectedContent: "0123456789ABCDEFGHIJ" + gap, start: 1.2, end: 4)
    }
    
    private func assertSliceString(slice: ContinuousSensorDataArray?, expectedContent: String, start: CFAbsoluteTime, end: CFAbsoluteTime) {
        XCTAssertTrue(slice != nil)
        XCTAssertTrue(slice!.sensorData.asString() == expectedContent,
            "start = \(start), end = \(end), expected = \(expectedContent), actual = \(slice!.sensorData.asString())")
    }

}

