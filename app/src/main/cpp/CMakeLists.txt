cmake_minimum_required(VERSION 3.22.1)
project("pocket_terminal")

add_library(pocket_terminal SHARED pty_bridge.cpp)

find_library(log-lib log)

target_link_libraries(pocket_terminal ${log-lib})