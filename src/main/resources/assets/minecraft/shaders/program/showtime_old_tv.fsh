#version 150

uniform sampler2D DiffuseSampler;
uniform vec2 OutSize;
uniform float Time;
uniform float Amount;

in vec2 texCoord;

out vec4 fragColor;

void main() {
    vec3 color = texture(DiffuseSampler, texCoord).rgb;
    float grey = dot(color, vec3(0.299, 0.587, 0.114));
    color = mix(color, vec3(grey), 0.55 * Amount);
    float scan = 0.82 + 0.18 * sin(texCoord.y * OutSize.y * 1.5);
    vec2 centred = texCoord - 0.5;
    float rim = 1.0 - dot(centred, centred) * 1.6 * Amount;
    float flicker = 0.96 + 0.04 * sin(Time * 6.2831 * 9.0);
    fragColor = vec4(color * scan * rim * flicker, 1.0);
}
