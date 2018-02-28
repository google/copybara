#!/usr/bin/env bash

java -jar /opt/copybara/copybara_deploy.jar $COPYBARA_OPTIONS $COPYBARA_SUBCOMMAND $COPYBARA_CONFIG $COPYBARA_WORKFLOW $COPYBARA_SOURCEREF
