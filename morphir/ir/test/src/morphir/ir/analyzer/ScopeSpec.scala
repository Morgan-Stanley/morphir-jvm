/*
Copyright 2020 Morgan Stanley

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/


package morphir.ir.analyzer

import zio.test._
import zio.test.Assertion._

object ScopeSpec extends DefaultRunnableSpec {
  def spec = suite("Scope Spec") {
    suite("PackageScope Spec")(
      suite("Creation")(
        test("A package scope should be creatable given some data") {
          case class PackageData(packageName: String)
          val data = PackageData("my.cool.package")

          val actual = Scope.pkg(data)
          assert(actual)(
            hasField("data", (s: PackageScope[PackageData]) => s.data, equalTo(data)) && hasField(
              "parent",
              _.parent,
              isUnit
            )
          )

        }
      )
    )
  }
}
