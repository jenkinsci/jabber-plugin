# Jenkins Jabber / XMPP Plugin

Integrates Jenkins with the Jabber/XMPP instant messaging protocol. Note
that you also need to install the [instant-messaging
plugin](http://wiki.jenkins-ci.org/display/JENKINS/Instant+Messaging+Plugin).

This plugin enables Jenkins to send build notifications via Jabber, as
well as let users talk to Jenkins via a 'bot' to run commands, query
build status etc.. 

## Installation Requirements

This plugin needs the [instant-messaging
plugin](http://wiki.jenkins-ci.org/display/JENKINS/Instant+Messaging+Plugin).
Please ensure that the latest version of this plug-in is also installed.

### Features

See [instant-messaging
plugin](http://wiki.jenkins-ci.org/display/JENKINS/Instant+Messaging+Plugin).

### User authenticated user for the command 'bot'

If you have a secure Jenkins that does not allow anonymous user to
build, you will need to have a user for Jabber. !build command can give
an error as discussed in
[JENKINS-15897](https://issues.jenkins-ci.org/browse/JENKINS-15897).  As
of version 1.35, the error is misleading as Jenkins complains about the
command syntax instead of unauthorized access error.

Adding a dedicated authenticated user for the bot could fix this
problem:

-   Add a new Jenkins user (will assume the username is *notification*)
-   If using *Matrix-based security* (Manage Jenkins -\> Configure
    Global Security)*,* add permission for the *notification* user for
    Job build, cancel, discover and read
-   Create a new global credentials and set the password for the user
    *notification*
-   In Jabber Plugin Configuration, set the Jenkins username to
    *notification*

### Debugging Problems

If you experience any problems using the plugin please increase the log
level of the logger *hudson.plugins.jabber* to FINEST (see ), try to
reproduce the problem and attach the collected logs to the JIRA issue.

## Contact

You can reach the developers in the

[smack@conference.igniterealtime.org](xmpp:smack@smack@conference.igniterealtime.org?join)

XMPP Multi-User Conference (MUC) room. If you do not have an XMPP
client at hand, then you may want to try this
[link](https://inverse.chat/#converse/room?jid=smack@conference.igniterealtime.org).

## Changelog

### Version 1.40 (2021-03-03)
- Update Smack to 4.4.1
- Fix [JENKINS-64247](https://issues.jenkins.io/browse/JENKINS-64247): jabber plugin save problem on Jenkins >= 2.264

### Version 1.39 (2020-06-07)
- Fix NPE in JabberPublisherDescriptor
- Bundle classes as JAR file into HPI file [JENKINS-54064](https://issues.jenkins-ci.org/browse/JENKINS-54064)

### Version 1.38 (2019-10-17)
-   Support Jenkins Pipeline for Jabber/XMPP notifications ([JENKINS-36826](https://issues.jenkins-ci.org/browse/JENKINS-36826))
-   Improved debug log messages

### Version 1.37 (2019-05-27)

-   Bumped Smack to 4.3.4 ([JENKINS-51487](https://issues.jenkins-ci.org/browse/JENKINS-51487))

### Version 1.36 (2017-07-22)

-   Bumped Smack to 4.1.9 ([JENKINS-45599](https://issues.jenkins-ci.org/browse/JENKINS-45599))

### Version 1.35 (2015-05-02)

-   Fixed 'retry with legacy SSL' logic if server doesn't respond at all
    to other connection attempts ([JENKINS-28169](https://issues.jenkins-ci.org/browse/JENKINS-28169))

### Version 1.33 (2015-02-16)

-   Don't make concurrent build wait because previous build finished yet
    (with instant-messaging-plugin 1.33) JENKINS-26892

### Version 1.31 (Dec. 20, 2014)

-   Re-enable vCard support and provide a real fix for JENKINS-25676
-   Fix: 'DNS name not found' bug (JENKINS-25505)

### Version 1.30 (Nov. 20, 2014)

-   Completely disable vCard support, because of another potential bug
    in Smack (JENKINS-25676)

### Version 1.29 (Nov. 10, 2014)

-   Workaround for vCard parsing problem in Smack library
    (JENKINS-25515)

### Version 1.28 (Oct. 28, 2014)

-   HipChat closes connections after 150s of inactivity. Implemented
    keep-alive for HipChat (JENKINS-25222)

### Version 1.27 (Oct. 26, 2014)

-   Fix: old messages in chatrooms weren't ignored by v1.26

### Version 1.26 (Oct. 14, 2014)

-   **Attention: Needs Java 7 at least. If you're still using Java 6,
    stay with version 1.25!**
-   add an option to accept all (untrusted) SSL certificates
-   Upgrade to Smack Api 4.0.4

### Version 1.25 (Dec. 15, 2012)

-   support resource in Jabber IDs
-   use configured nickname as general nick, too, not just in chatrooms
    (JENKINS-11903)

### Version 1.24 (Oct. 13, 2012)

-   Fixed a bug when upgrading from older versions on Windows
    (JENKINS-15469)

### Version 1.23

-   See [instant-messaging
    plugin](http://wiki.jenkins-ci.org/display/HUDSON/Instant+Messaging+Plugin) 1.22
    for new features. Also:
-   fixed NullPointerException when migrating from a previous version
    and no group chats were configured (JENKINS-13925)

### Version 1.22

See [instant-messaging
plugin](http://wiki.jenkins-ci.org/display/JENKINS/Instant+Messaging+Plugin) 1.21
for new features.

### Version 1.21

-   support password-protected chat rooms (JENKINS-11407)
-   fixed problem with e-mail address as jabber id option
    (JENKINS-11443)

### Version 1.20

-   the bot user now has a nice vCard/avatar image

### Version 1.19

-   DNS lookup of Jabber server by servicename didn't work
    (JENKINS-10523)
-   new option to allow e-mail addresses to be used as Jabber ids
    (JENKINS-8594)

### Version 1.18

-   bot commands via private message were not recognized under certain
    circumstances (JENKINS-9954)

### Version 1.17

-   upgrade Smack library to 3.2 - fixes several problems with ejabberd
    and googletalk (JENKINS-5345, JENKINS-7060, JENKINS-8426)

### Version 1.16

-   See Instant-Messaging plugin 1.16 for new features

### Version 1.15

-   See Instant-Messaging plugin 1.15 for new features

### Version 1.14

-   proxy support (thanks to felfert for the patch)
-   see [instant-messaging
    plugin](http://wiki.jenkins-ci.org/display/JENKINS/Instant+Messaging+Plugin)
    1.14 for further changes!

### Version 1.13

-   new feature: new chat notifier which prints the failing tests, too
    [JENKINS-7035](http://issues.jenkins-ci.org/browse/JENKINS-7035)

### Version 1.12

-   improvement: bot commands are now extensible and open for other
    plugins (see class BotCommand).
-   improvement: added an extension point to customize the message the
    bot sends to chats for notification (see class BuildToChatNotifier).
-   improvement: re-added legacy SSL support (seems like there are still
    some XMPP servers out there which need it)
    ([JENKINS-6863](http://issues.jenkins-ci.org/browse/JENKINS-6863) )

### Version 1.11

-   fixed: disconnects (and no reconnects) when changing the global
    config
    ([JENKINS-6993](http://issues.jenkins-ci.org/browse/JENKINS-6993))
-   improved behaviour when plugin is disabled. I.e. doesn't log
    unnecessary stuff.
-   fixed: plugin's configure option not visible
    [JENKINS-5978](http://issues.jenkins-ci.org/browse/JENKINS-5978)
    [JENKINS-5233](http://issues.jenkins-ci.org/browse/JENKINS-5233)

### Version 1.10

-   fixed: *notify upstream commiter* would have notified committers of
    'old' builds
    ([JENKINS-6712](http://issues.jenkins-ci.org/browse/JENKINS-6712))
-   improvement: print useful project names for matrix jobs
    ([JENKINS-6560](http://issues.jenkins-ci.org/browse/JENKINS-6560) )
-   fixed: don't delay Hudson startup
    ([JENKINS-4346](http://issues.jenkins-ci.org/browse/JENKINS-4346) )
-   feature: *userstat* command for bot
    ([JENKINS-6147](http://issues.jenkins-ci.org/browse/JENKINS-6147) )
-   fixed: don't count offline computer for the executors count
    ([JENKINS-6387](http://issues.jenkins-ci.org/browse/JENKINS-6387))
-   improvement: print fully qualified Jabber ID in build cause
    ([JENKINS-4970](http://issues.jenkins-ci.org/browse/JENKINS-4970) )

### Version 1.9

-   allow to pass build parameters with the *build* command
    ([JENKINS-5058](http://issues.jenkins-ci.org/browse/JENKINS-5058) )
-   fixed: bot disconnected from conferences *when expose* presence was
    *false*
    ([JENKINS-6101](http://issues.jenkins-ci.org/browse/JENKINS-6101) )

### Version 1.8

-   fixed connection problem with eJabberd
    ([JENKINS-6032](http://issues.jenkins-ci.org/browse/JENKINS-6032))
-   fixed connection problem with GoogleTalk
    ([JENKINS-6009](http://issues.jenkins-ci.org/browse/JENKINS-6009),
    [JENKINS-6018](http://issues.jenkins-ci.org/browse/JENKINS-6018))
    -   if connection does not out-of-the-box, please disable SASL
        authentication (new Jabber option under *'Configure System'*)
        and try again
-   fixed connection problem with Prosody server
    ([JENKINS-5803](http://issues.jenkins-ci.org/browse/JENKINS-5803))

### Version 1.7

**ATTENTION again: there seem to be issues authenticating to several
Jabber servers with this release. So currently the best option is to
stay with Jabber v1.5 and instant-messaging v1.4. Sorry for all the
inconvenience!**

-   fixed regression introduced in 1.6: old target configurations
    weren't read correctly
    ([JENKINS-5976](http://issues.jenkins-ci.org/browse/JENKINS-5976))
-   upgrade to Smack library 3.1.0
    ([JENKINS-5805](http://issues.jenkins-ci.org/browse/JENKINS-5805))
    -   **ATTENTION: Legacy SSL is no longer supported. If you really
        need Legacy SSL, you should stay with v1.5 and instant-messaging
        plugin v1.4.**
-   Acceptance mode for subscription requests is now configurable
    ([JENKINS-5836](http://issues.jenkins-ci.org/browse/JENKINS-5836))
    -   Attention: this feature is not tested thoroughly, yet

### Version 1.6

**ATTENTION: there seems to be an issue with reading old configs. I'll
publish a fixed version shortly!**

-   compatibility with instant-messaging plugin 1.5

### Version 1.5

-   new option to inform upstream committers
    ([JENKINS-4629](http://issues.jenkins-ci.org/browse/JENKINS-4629) )

### Version 1.4

-   compatibility with instant-messaging plugin 1.3
-   some very minor issues

### Version 1.3

-   fixed: NullPointerException if plugin was disabled in global config

### Version 1.2

-   fixed wrong order of Hudson credentials
    \[[JENKINS-4721](http://issues.jenkins-ci.org/browse/JENKINS-4721)
    \]

### Version 1.1

-   works in secured Hudson instances if you specify a username and
    password for the Jabber bot
-   new !comment command which adds a description to builds
-   show dependency to instant-messaging plugin in update center
    description (D'oh, that didn't work as expected. Hope it will in
    1.2)
-   this version needs Hudson 1.319 or higher

### Version 1.0

-   This is the first release which is based on the
    -   Make sure that the instant messaging plugin is installed, too
    -   ATTENTION: Although much care has been taken to make this
        version compatible with 0.11 it cannot be guaranteed that all
        configuration options can be migrated successfully!
-   enable/disable checkbox
    \[[JENKINS-2495](http://issues.jenkins-ci.org/browse/JENKINS-2495)\] 
-   made reconnection logic more robust
-   fixed status not going back to 'available' after build finishes
    (again)
    \[[JENKINS-4337](http://issues.jenkins-ci.org/browse/JENKINS-4337)\]

### Version 0.11

-   fixed per-job configuration display
-   fixed jabber user-property not being visible
-   command aliases in bot
-   fixed status not going back to 'available' after build finishes
    \[[JENKINS-4337](http://issues.jenkins-ci.org/browse/JENKINS-4337)\]
-   option to inform 'culprits' on subsequent build failures

### Version 0.10

-   fixed infinite loop if previous build was aborted
    \[[JENKINS-4290](http://issues.jenkins-ci.org/browse/JENKINS-4290)\]
-   plugin's Jabber status changes based on Hudson's busy state
    \[[JENKINS-620](http://issues.jenkins-ci.org/browse/JENKINS-620)\]
-   fixed handling of single quotes
    \[[JENKINS-3215](http://issues.jenkins-ci.org/browse/JENKINS-3215)\]
-   started refactoring to split-out protocol independent part in a
    shareable plugin

### Version 0.9

-   automatic reconnect on lost connections
-   new notification strategy: "failures and fixes"
-   default suffix for Jabber IDs. When entered, Jabber IDs can be
    inferred from Hudson user ids  
    I.e. Jabber ID = \<hudson\_id\>\<extension\> ([issue
    \#1527](http://issues.jenkins-ci.org/browse/JENKINS-1527))
-   'botsnack' command massively improved
-   'status' command can show all jobs for a view (-v \<view\>)
-   new 'health' command
-   bot now also works in 1-on-1 chat ([issue
    4057](http://issues.jenkins-ci.org/browse/JENKINS-4057))
-   lots of refactorings and code clean up

### Version 0.8

-   Two new commands are added: "!testresult" and "!abort"

### Version 0.7

-   URL Encoding in messages for SCM suspects ([issue
    \#2693](http://issues.jenkins-ci.org/browse/JENKINS-2693))
-   Add legacy SSL support and alternate nickname for group-chat ([issue
    \#2699](http://issues.jenkins-ci.org/browse/JENKINS-2699))
-   Jobs with spaces can now be launched via messages ([issue
    \#2700](http://issues.jenkins-ci.org/browse/JENKINS-2700))

### Version 0.6

-   Notification for maven builds

### Version 0.5

-   Updated to work with Hudson 1.218.
-   Fix for url encoding ([issue
    \#909](http://issues.jenkins-ci.org/browse/JENKINS-909))

### 0.4 (2007/11/22)

-   Organized the configuration screen for gradual exposure to details.
-   The plugin now works with Google Talk. (JENKINS-1021)
-   Improved help documentation.
-   Fixed NPE when no initial group chat is configured.
