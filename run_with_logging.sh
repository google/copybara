#!/bin/bash
#
# Script to run Copybara with logging enabled by default
# This ensures that the commit search logging we added will be visible

# Set up logging configuration
log_config=$(mktemp)
cat > $log_config <<'EOF'
handlers=java.util.logging.ConsoleHandler,java.util.logging.FileHandler
.level=INFO
java.util.logging.ConsoleHandler.level=INFO
java.util.logging.ConsoleHandler.formatter=java.util.logging.SimpleFormatter
java.util.logging.ConsoleHandler.encoding=UTF-8
java.util.logging.FileHandler.level=INFO
java.util.logging.FileHandler.pattern=copybara-%g.log
java.util.logging.FileHandler.count=10
java.util.logging.FileHandler.formatter=java.util.logging.SimpleFormatter
java.util.logging.SimpleFormatter.format=%1$tY-%1$tm-%1$td %1$tH:%1$tM:%1$tS %4$-6s %2$s %5$s%6$s%n
EOF

# Run Copybara with the logging configuration
echo "Running Copybara with commit search logging enabled..."
echo "Logs will appear both in console and in copybara-*.log files"
echo ""

# Pass all arguments to Copybara
java -Djava.util.logging.config.file=$log_config -jar copybara.jar "$@"

# Clean up
rm -f $log_config


