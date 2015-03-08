INCLUDE_DIRECTORIES(/usr/local/opt/pebble-sdk/Pebble/include)

ADD_SUBDIRECTORY(main)
ADD_SUBDIRECTORY(test)

ADD_TEST(
  NAME aa-core-test 
  COMMAND aa-core-test
  WORKING_DIRECTORY "${CMAKE_CURRENT_BINARY_DIR}/test"
)
