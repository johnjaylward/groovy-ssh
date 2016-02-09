package org.hidetake.groovy.ssh.test.server

import com.jcraft.jsch.JSchException
import org.apache.sshd.SshServer
import org.apache.sshd.server.CommandFactory
import org.apache.sshd.server.PasswordAuthenticator
import org.apache.sshd.server.PublickeyAuthenticator
import org.hidetake.groovy.ssh.Ssh
import org.hidetake.groovy.ssh.core.Service
import org.hidetake.groovy.ssh.test.SshServerMock
import org.hidetake.groovy.ssh.test.UserKeyFixture
import spock.lang.Specification
import spock.lang.Unroll

import java.security.PublicKey

import static org.hidetake.groovy.ssh.test.SshServerMock.commandWithExit
import static org.hidetake.groovy.ssh.test.UserKeyFixture.KeyType.*

class AuthenticationSpec extends Specification {

    SshServer server

    Service ssh

    def setup() {
        server = SshServerMock.setUpLocalhostServer()
        server.commandFactory = Mock(CommandFactory)

        ssh = Ssh.newService()
        ssh.settings {
            knownHosts = allowAnyHosts
        }
    }

    def cleanup() {
        server.stop(true)
    }

    def "password authentication should pass if exact one is given"() {
        given:
        server.passwordAuthenticator = Mock(PasswordAuthenticator)
        server.start()

        ssh.remotes {
            testServer {
                host = server.host
                port = server.port
                user = 'someuser'
                password = 'somepassword'
            }
        }

        when:
        ssh.run {
            session(ssh.remotes.testServer) {
                execute 'ls'
            }
        }

        then:
        1 * server.passwordAuthenticator.authenticate('someuser', 'somepassword', _) >> true

        then:
        1 * server.commandFactory.createCommand('ls') >> commandWithExit(0)
    }

    def "password authentication should fail if wrong one is given"() {
        given:
        server.passwordAuthenticator = Mock(PasswordAuthenticator)
        server.start()

        ssh.remotes {
            testServer {
                host = server.host
                port = server.port
                user = 'someuser'
                password = 'wrongpassword'
            }
        }

        when:
        ssh.run {
            session(ssh.remotes.testServer) {
                execute 'ls'
            }
        }

        then:
        (1.._) * server.passwordAuthenticator.authenticate('someuser', 'wrongpassword', _) >> false

        then:
        0 * server.commandFactory.createCommand(_)

        and:
        JSchException e = thrown()
        e.message == 'Auth fail'
    }

    @Unroll
    def "public key authentication should pass if valid #keyType #type is given"() {
        given:
        server.publickeyAuthenticator = Mock(PublickeyAuthenticator)
        server.start()

        ssh.remotes {
            testServer {
                host = server.host
                port = server.port
                user = 'someuser'
                identity = identitySetting
            }
        }

        when:
        ssh.run {
            session(ssh.remotes.testServer) {
                execute 'ls'
            }
        }

        then:
        (1.._) * server.publickeyAuthenticator.authenticate(
                'someuser', { PublicKey k -> k.algorithm == keyType } as PublicKey, _) >> true

        then:
        1 * server.commandFactory.createCommand('ls') >> commandWithExit(0)

        where:
        keyType | type      | identitySetting
        'RSA'   | 'File'   | UserKeyFixture.privateKey(rsa)
        'RSA'   | 'String' | UserKeyFixture.privateKey(rsa).text
        'EC'    | 'File'   | UserKeyFixture.privateKey(ecdsa)
        'EC'    | 'String' | UserKeyFixture.privateKey(ecdsa).text
    }

    @Unroll
    def "public key authentication should pass if valid #keyType #type is given in global settings"() {
        given:
        server.publickeyAuthenticator = Mock(PublickeyAuthenticator)
        server.start()

        ssh.settings {
            identity = identitySetting
        }

        ssh.remotes {
            testServer {
                host = server.host
                port = server.port
                user = 'someuser'
            }
        }

        when:
        ssh.run {
            session(ssh.remotes.testServer) {
                execute 'ls'
            }
        }

        then:
        (1.._) * server.publickeyAuthenticator.authenticate(
                'someuser', { PublicKey k -> k.algorithm == keyType } as PublicKey, _) >> true

        then:
        1 * server.commandFactory.createCommand('ls') >> commandWithExit(0)

        where:
        keyType | type      | identitySetting
        'RSA'   | 'File'   | UserKeyFixture.privateKey(rsa)
        'RSA'   | 'String' | UserKeyFixture.privateKey(rsa).text
        'EC'    | 'File'   | UserKeyFixture.privateKey(ecdsa)
        'EC'    | 'String' | UserKeyFixture.privateKey(ecdsa).text
    }

    @Unroll
    def "public key authentication should fail if wrong key #type is given"() {
        given:
        server.publickeyAuthenticator = Mock(PublickeyAuthenticator)
        server.start()

        ssh.remotes {
            testServer {
                host = server.host
                port = server.port
                user = 'someuser'
                identity = UserKeyFixture.privateKey()
            }
        }

        when:
        ssh.run {
            session(ssh.remotes.testServer) {
                execute 'ls'
            }
        }

        then:
        (1.._) * server.publickeyAuthenticator.authenticate('someuser', _ as PublicKey, _) >> false

        then:
        0 * server.commandFactory.createCommand(_)

        and:
        JSchException e = thrown()
        e.message == 'Auth fail'

        where:
        type      | identitySetting
        'File'   | UserKeyFixture.privateKey()
        'String' | UserKeyFixture.privateKey().text
    }

    def "public key authentication should accept the passphrase of identity"() {
        given:
        server.publickeyAuthenticator = Mock(PublickeyAuthenticator)
        server.start()

        ssh.remotes {
            testServer {
                host = server.host
                port = server.port
                user = 'someuser'
                identity = UserKeyFixture.privateKey(rsa_pass)
                passphrase = "gradle"
            }
        }

        when:
        ssh.run {
            session(ssh.remotes.testServer) {
                execute 'ls'
            }
        }

        then:
        (1.._) * server.publickeyAuthenticator.authenticate('someuser', { PublicKey k -> k.algorithm == 'RSA' } as PublicKey, _) >> true

        then:
        1 * server.commandFactory.createCommand('ls') >> commandWithExit(0)
    }

    def "identity and passphrase can be set by global settings"() {
        given:
        server.publickeyAuthenticator = Mock(PublickeyAuthenticator)
        server.start()

        ssh.settings {
            identity = UserKeyFixture.privateKey(rsa_pass)
            passphrase = "gradle"
        }

        ssh.remotes {
            testServer {
                host = server.host
                port = server.port
                user = 'someuser'
            }
        }

        when:
        ssh.run {
            session(ssh.remotes.testServer) {
                execute 'ls'
            }
        }

        then:
        (1.._) * server.publickeyAuthenticator.authenticate('someuser', { PublicKey k -> k.algorithm == 'RSA' } as PublicKey, _) >> true

        then:
        1 * server.commandFactory.createCommand('ls') >> commandWithExit(0)
    }

    def "public key authentication should fail if wrong passphrase is given"() {
        given:
        server.publickeyAuthenticator = Mock(PublickeyAuthenticator)
        server.start()

        ssh.remotes {
            testServer {
                host = server.host
                port = server.port
                user = 'someuser'
                identity = UserKeyFixture.privateKey(rsa_pass)
                passphrase = "wrong"
            }
        }

        when:
        ssh.run {
            session(ssh.remotes.testServer) {
                execute 'ls'
            }
        }

        then:
        (1.._) * server.publickeyAuthenticator.authenticate('someuser', { PublicKey k -> k.algorithm == 'RSA' } as PublicKey, _) >> true

        then:
        0 * server.commandFactory.createCommand(_)

        and:
        JSchException e = thrown()
        e.message == 'USERAUTH fail'
    }

    def "remote specific identity should precede one in global settings"() {
        given:
        server.publickeyAuthenticator = Mock(PublickeyAuthenticator)
        server.start()

        ssh.settings {
            identity = UserKeyFixture.privateKey(rsa)
        }

        ssh.remotes {
            testServer {
                host = server.host
                port = server.port
                user = 'someuser'
                identity = UserKeyFixture.privateKey(rsa_pass)
            }
        }

        when:
        ssh.run {
            session(ssh.remotes.testServer) {
                execute 'ls'
            }
        }

        then:
        (1.._) * server.publickeyAuthenticator.authenticate('someuser', { PublicKey k -> k.algorithm == 'RSA' } as PublicKey, _) >> true

        then:
        0 * server.commandFactory.createCommand(_)

        then:
        JSchException e = thrown()
        e.message == 'USERAUTH fail'
    }

}
