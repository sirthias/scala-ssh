# test host file
login-type  = keyfile
username    = alice
keyfile     = /some/file
host-name   = xyz.special.com
port        = 30