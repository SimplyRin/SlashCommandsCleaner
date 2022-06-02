# SlashCommandsCleaner
Minecraft 1.13 以降でプレイヤーが `/` を入力し `Tab` を押すと表示されるコマンド一覧を隠せるプラグイン

[BungeeCord-r v9](https://ci.simplyrin.net/job/BungeeCord/) で動作確認

# Permission
- slashcommandscleaner.bypass: 権限所持者に通常のリストを返します。
- slashcommandscleaner.member: 権限所持者に `fakelist.member` で設定されているリストを返します。
- slashcommandscleaner.plus: 権限所持者に `fakelist.plus` で設定されているリストを返します。

`member, plus` 以外でも `fakelist.xxxxx` に追加すれば反映されます。

# Default Config
```yaml
fakelist:
  default:
  - help
  - list
  - me
  - msg
  - teammsg
  - tell
  - tm
  - trigger
  - w
  member:
  - ++default
  - time
  - weather
  plus:
  - ++member
  - gamemode
```

※ `++` から始まるノードは親グループを示します。

# Screenshot

![javaw](https://github.com/PegSaba/SlashCommandsCleaner/blob/master/images/2022-04-14-21_51_43_javaw.png)

# Open Source License

**・[BungeeCord](https://github.com/SpigotMC/BungeeCord/blob/master/LICENSE)**
```
Copyright (c) 2012, md_5. All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:

Redistributions of source code must retain the above copyright notice, this
list of conditions and the following disclaimer.

Redistributions in binary form must reproduce the above copyright notice,
this list of conditions and the following disclaimer in the documentation
and/or other materials provided with the distribution.

The name of the author may not be used to endorse or promote products derived
from this software without specific prior written permission.

You may not use the software for commercial software hosting services without
written permission from the author.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE
LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
POSSIBILITY OF SUCH DAMAGE.
```
