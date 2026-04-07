package com.example.mytvxml

data class ShellResult(val exitCode: Int,
                       val stdout: String,
                       val stderr: String,
                       val command: String)
