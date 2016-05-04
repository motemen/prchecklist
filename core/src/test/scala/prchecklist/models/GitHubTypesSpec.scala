package prchecklist.models

import prchecklist.test.Factory

import org.scalatest._

class GitHubTypesSpec extends FunSuite with Matchers with OptionValues {
  test("PullRequest#isOpen/isClosed") {
    val pr = Factory.createGitHubPullRequest
    pr.copy(state = "open").isOpen shouldBe true
    pr.copy(state = "closed").isOpen shouldBe false

    pr.copy(state = "open").isClosed shouldBe false
    pr.copy(state = "closed").isClosed shouldBe true
  }

  test("Content#fileContent") {
    val content = GitHubTypes.Content(
      `type`   = "file",
      encoding = "base64",
      content  = "PSBwcmNoZWNrbGlzdAoKaW1hZ2U6aHR0cHM6Ly9iYWRnZXMuZ2l0dGVyLmlt\nL21vdGVtZW4vcHJjaGVja2xpc3Quc3ZnW2xpbms9Imh0dHBzOi8vZ2l0dGVy\nLmltL21vdGVtZW4vcHJjaGVja2xpc3Q/dXRtX3NvdXJjZT1iYWRnZSZ1dG1f\nbWVkaXVtPWJhZGdlJnV0bV9jYW1wYWlnbj1wci1iYWRnZSZ1dG1fY29udGVu\ndD1iYWRnZSJdCgo9PSBSZXF1aXJlbWVudHMKCi0gUG9zdGdyZXNxbAotIFJl\nZGlzCgo9PT0gUmVnaXN0ZXIgYSBkZXZlbG9wZXIgYXBwbGljYXRpb24KCi0g\nVmlzaXQgaHR0cHM6Ly9naXRodWIuY29tL3NldHRpbmdzL2RldmVsb3BlcnMg\nYW5kIHJlZ2lzdGVyIG9uZQotIENyZWF0ZSBhIG5ldyBmaWxlIG5hbWVkIGBs\nb2NhbC5zYnRgIGF0IHRoZSB0b3Agb2YgdGhlIHByb2plY3QsIHdob3NlIGNv\nbnRlbnQgaXMgbGlrZTogKwotLS0tCmphdmFPcHRpb25zICsrPSBTZXEoCiAg\nIi1EZ2l0aHViLmNsaWVudElkPTxZb3VyIEdpdEh1YiBDbGllbnQgSUQ+IiwK\nICAiLURnaXRodWIuY2xpZW50U2VjcmV0PTxZb3VyIEdpdEh1YiBDbGllbnQg\nU2VjcmV0IgopCi0tLS0KCj09IERldmVsb3BtZW50CgotLS0tCiQgY3JlYXRl\nZGIgcHJjaGVja2xpc3RfbG9jYWwKJCBwc3FsIHByY2hlY2tsaXN0X2xvY2Fs\nIDwgZGIvcHJjaGVja2xpc3Quc3FsCiQgLi9zYnQKPiBkZXZlbAojIFZpc2l0\nIGh0dHA6Ly9sb2NhbGhvc3Q6MzAwMAotLS0tCgo9PSBUT0RPCgoqIFVuaWZ5\nIGludGVyZmFjZXMgdG8gc2NhbGEgRnV0dXJlcyBhbmQgc2NhbGF6IFRhc2tz\nLgoqIFJlcG8gdmlzaWJpbGl0eQoqIFVJCg==\n"
    )
    content.fileContent.value should startWith("= prchecklist\n\n")
  }
}
