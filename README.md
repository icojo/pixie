Start the application with: sbt run

Make sure to have ports 4444 and 8080 available as it will use the former for a serverSocket and the latter for a httpServer.

Once started the app runs the binary that generates data (e.g: { "event_type": "foo", "data": "lorem", "timestamp": 1594898762 }) and will write that data to the socket outputStream.

A spark streaming job will connect to the socket and performs a windowed (10 seconds window and slide) word count, grouped by event_type.

Once the application is started you can check at http://127.0.0.1:8080 what the results are.

 